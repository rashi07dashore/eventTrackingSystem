# Event Ticketing System — Flow Diagrams

High-level flows for **users**, **admins**, and **data stores** (MySQL, MongoDB, Redis).

---

## 1. User flow (end-to-end)

From signup to confirmed booking and payment.

```mermaid
flowchart LR
    subgraph Auth
        A[Signup] --> B[Login]
        B --> C[JWT Token]
    end
    subgraph Discover
        C --> D[GET /events]
        D --> E[GET /events/:id]
        E --> F[GET /events/:id/seats]
    end
    subgraph Book
        F --> G[POST lock-seat]
        G --> H[POST create-booking]
        H --> I[POST payments/pay]
        I --> J[Confirmed]
    end
    Auth --> Discover --> Book
```

**Detailed user journey:**

```mermaid
sequenceDiagram
    participant U as User
    participant API as API
    participant Redis as Redis
    participant Mongo as MongoDB
    participant MySQL as MySQL

    U->>API: POST /signup (name, email, password)
    API->>MySQL: INSERT user (BCrypt hash)
    MySQL-->>API: OK
    API-->>U: User Created

    U->>API: POST /login (email, password)
    API->>MySQL: GET user by email
    MySQL-->>API: user row
    API-->>U: JWT (userId, email, role)

    U->>API: GET /events?city=&date= (optional)
    API->>Mongo: find events
    Mongo-->>API: events
    API-->>U: events list

    U->>API: GET /events/:id/seats
    API->>Mongo: get event seats
    Mongo-->>API: seats (AVAILABLE/LOCKED/BOOKED)
    API-->>U: seat map

    U->>API: POST /events/:id/lock-seat (seatNumbers) + JWT
    API->>Redis: SET seat_lock:eventId:seat NX EX 300 (value=userId)
    Redis-->>API: OK
    API->>Mongo: update seats → LOCKED
    API-->>U: 200 OK

    U->>API: POST /events/:id/create-booking (seatNumbers) + JWT
    API->>Redis: GET keys → validate userId owns locks
    API->>Mongo: get event, sum seat prices
    API->>MySQL: INSERT booking (PENDING)
    API-->>U: bookingId, totalAmount

    U->>API: POST /payments/pay (bookingId) + JWT
    API->>MySQL: get booking, check PENDING + ownership
    API->>Redis: DEL lock keys
    API->>MySQL: UPDATE booking → CONFIRMED, payment SUCCESS
    API->>Mongo: update seats → BOOKED
    API-->>U: SUCCESS
```

---

## 2. Admin flow

Admin-only: create events with seats and show timings.

```mermaid
flowchart LR
    A[Login as Admin] --> B[JWT with role=admin]
    B --> C[POST /events]
    C --> D[Create event in MongoDB]
    D --> E[Event + seats AVAILABLE]
```

**Admin create-event sequence:**

```mermaid
sequenceDiagram
    participant Admin
    participant API
    participant MySQL
    participant Mongo

    Admin->>API: POST /login (admin email)
    API->>MySQL: GET user by email
    MySQL-->>API: user
    Note over API: ADMIN_EMAILS.contains(email) → role=admin
    API-->>Admin: JWT (role: admin)

    Admin->>API: POST /events (name, location, city, date, showTimings, seats) + JWT
    API->>API: JWT role == admin?
    alt Not admin
        API-->>Admin: 403 Forbidden
    else Admin
        API->>Mongo: insert event doc (_id, name, location, city, date, showTimings, seats[])
        Note over Mongo: each seat: seatNumber, category, price, status=AVAILABLE
        Mongo-->>API: OK
        API-->>Admin: 201 / event id
    end
```

---

## 3. Database usage (which store does what)

```mermaid
flowchart TB
    subgraph MySQL["MySQL — Users & Bookings"]
        U[users: id, name, email, password_hash]
        B[bookings: id, user_id, event_id, seat_numbers, total_amount, status, payment_status]
    end

    subgraph Mongo["MongoDB — Events & Seats"]
        E[events: _id, name, location, city, date, showTimings]
        S[seats: seatNumber, category, price, status]
        E --> S
    end

    subgraph Redis["Redis — Locks & Rate limits"]
        L["seat_lock:eventId:seatNumber → userId (TTL 300s)"]
        R["rate_limit:userId → count (TTL 60s)"]
    end

    API[API] --> MySQL
    API --> Mongo
    API --> Redis
```

**Per-operation store usage:**

| Operation            | MySQL        | MongoDB           | Redis                    |
|----------------------|-------------|-------------------|--------------------------|
| Signup               | INSERT user | —                 | —                        |
| Login                | SELECT user | —                 | —                        |
| Get events / seats   | —           | find events       | —                        |
| Lock seat            | —           | update LOCKED      | SET NX EX 300, rate incr |
| Create booking       | INSERT booking (PENDING) | find event (prices) | GET (validate locks) |
| Pay (success)        | UPDATE CONFIRMED | update BOOKED   | DEL lock keys            |
| Pay (failure)        | UPDATE FAILED | update AVAILABLE | DEL lock keys            |
| Lock expiry job      | —           | LOCKED → AVAILABLE when key missing | EXISTS check |
| Lock status (UI)     | —           | —                 | KEYS, GET, TTL           |
| Admin create event   | —           | insert event doc  | —                        |

---

## 4. Lock lifecycle (Redis + MongoDB sync)

How seat locks are created, used, and cleaned up.

```mermaid
stateDiagram-v2
    [*] --> AVAILABLE: Seat created (admin)
    AVAILABLE --> LOCKED: User lock-seat (Redis SET NX EX 300)
    LOCKED --> BOOKED: User pays (Redis DEL, Mongo BOOKED)
    LOCKED --> AVAILABLE: Payment failed (Redis DEL, Mongo AVAILABLE)
    LOCKED --> AVAILABLE: TTL expired (LockExpiryJob: Redis key gone → Mongo AVAILABLE)
```

**Lock expiry job (every 60s):**

```mermaid
flowchart LR
    A[LockExpiryJob] --> B[Find all events in Mongo]
    B --> C[For each seat with status LOCKED]
    C --> D[Redis EXISTS seat_lock:eventId:seat]
    D --> E{Key exists?}
    E -->|Yes| F[Leave LOCKED]
    E -->|No| G[Update Mongo: seat → AVAILABLE]
```

---

## 5. Request routing (API entry points)

```mermaid
flowchart TB
    R[Router] --> Health["/health (no auth)"]
    R --> Auth
    R --> Public
    R --> Admin
    R --> Protected
    R --> Payment

    Auth --> Signup["POST /signup"]
    Auth --> Login["POST /login"]

    Public --> GetEvents["GET /events"]
    Public --> GetEvent["GET /events/:id"]
    Public --> GetSeats["GET /events/:id/seats"]

    Admin --> CreateEvent["POST /events (JWT + role=admin)"]

    Protected --> LockStatus["GET /events/:id/lock-status"]
    Protected --> LockSeat["POST /events/:id/lock-seat (rate limit → handler)"]
    Protected --> CreateBooking["POST /events/:id/create-booking"]
    Protected --> Book["POST /events/:id/book"]

    Payment --> Pay["POST /payments/pay (JWT)"]
```

---

## 6. Cross-store booking flow (summary)

Diagram showing which store is touched at each step.

```mermaid
flowchart LR
    subgraph Step1[Lock]
        U1[User] -->|lock-seat| R1[Redis: SET NX EX]
        R1 --> M1[Mongo: LOCKED]
    end
    subgraph Step2[Create booking]
        U2[User] -->|create-booking| R2[Redis: GET validate]
        R2 --> Mo2[Mongo: prices]
        Mo2 --> My2[MySQL: INSERT PENDING]
    end
    subgraph Step3[Pay]
        U3[User] -->|pay| R3[Redis: DEL]
        R3 --> My3[MySQL: CONFIRMED]
        My3 --> M3[Mongo: BOOKED]
    end
    Step1 --> Step2 --> Step3
```

---