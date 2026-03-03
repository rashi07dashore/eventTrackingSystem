# Event Ticketing System

A backend API for event ticketing: user auth, event listing, seat locking, booking, and simulated payment. Built with **Vert.x** (Java) and multiple data stores to practice **high-level design** (HLD) and **low-level design** (LLD).

---

## Features

- **Auth:** Signup, login (JWT), admin role via config
- **Events:** List/filter by city and date, get event details and seat map (MongoDB)
- **Seat locking:** Temporary lock with Redis TTL (5 min); rate limited per user
- **Booking:** Create pending booking → pay (simulated) → confirm; or legacy direct book
- **Lock expiry:** Background job releases seats when Redis lock expires
- **APIs:** REST; CORS enabled; health check endpoint

---

## Tech Stack

| Layer        | Technology        |
|-------------|-------------------|
| Runtime     | Java 21, Vert.x 4 |
| API         | Vert.x Web, JWT   |
| Relational  | MySQL 8 (users, bookings) |
| Document    | MongoDB 7 (events, seats)  |
| Cache/Lock  | Redis 7 (locks, rate limit) |
| Build       | Maven             |

---

## Architecture (High-Level)

```
                    ┌─────────────────┐
                    │   Vert.x HTTP   │
                    │   (port 8080)   │
                    └────────┬────────┘
                             │
         ┌───────────────────┼───────────────────┐
         ▼                   ▼                    ▼
   ┌──────────┐       ┌────────────┐        ┌──────────┐
   │  MySQL   │       │  MongoDB   │        │  Redis   │
   │ users,   │       │ events,    │        │ locks,   │
   │ bookings │       │ seats      │        │ rate     │
   └──────────┘       └────────────┘        └──────────┘
```

- **MySQL:** Source of truth for users and bookings (status, payment).
- **MongoDB:** Events and seat layout (AVAILABLE / LOCKED / BOOKED).
- **Redis:** Short-lived seat locks (TTL) and per-user rate limits for lock attempts.

**📐 Design & Architecture Flow** — Detailed documentation of the system design, architecture, and execution flow is available in [flow.md](FLOW.md).

---

## Project Structure

```
eventTrackingSystem/
├── src/main/java/com/ticketing/
│   ├── MainVerticle.java          # Server bootstrap, routes, JWT
│   ├── config/
│   │   ├── AppConfig.java         # Env (dotenv)
│   │   └── DatabaseConfig.java    # MySQL, Mongo, Redis clients
│   ├── handler/
│   │   ├── AuthHandler.java       # Signup, login
│   │   ├── EventHandler.java      # List events, get event, get seats
│   │   ├── AdminEventHandler.java # Create event (admin)
│   │   ├── BookingHandler.java    # Lock seat, create booking, book (legacy)
│   │   ├── LockStatusHandler.java # Lock status + TTL
│   │   ├── PaymentHandler.java    # Simulated pay
│   │   └── RateLimitHandler.java  # Lock-seat rate limit
│   ├── job/
│   │   └── LockExpiryJob.java     # Periodic release of expired locks
│   └── utils/
│       └── QueryUtils.java        # SQL constants
├── docker/
│   └── mysql/
│       └── init.sql               # Schema: users, bookings
├── docker-compose.yml             # MySQL, MongoDB, Redis
├── .env.example                   # Env template
├── EventTicketingSystem.postman_collection.json
├── DOCKER_README.md               # Detailed run & Postman guide
└── pom.xml
```

---

## Prerequisites

- **Java 21** and **Maven**
- **Docker** and **Docker Compose** (for MySQL, MongoDB, Redis)

---

## Quick Start

1. **Start databases (Docker):**

   ```bash
   cd eventTrackingSystem
   docker compose up -d
   ```

2. **Configure environment:**

   ```bash
   cp .env.example .env
   # Edit .env if needed (defaults match docker-compose)
   # Set ADMIN_EMAILS for admin-only APIs (e.g. create event)
   ```

3. **Run the application:**

   ```bash
   mvn compile exec:java -q
   ```

   Server runs on **http://localhost:8080** (or `SERVER_PORT` from `.env`).

4. **Test APIs:** Import `EventTicketingSystem.postman_collection.json` into Postman. Use **Login** to set the JWT; then call **Lock Seat** → **Create Booking** → **Pay**.

For step-by-step instructions, troubleshooting, and Postman flow, see **[DOCKER_README.md](eventTrackingSystem/DOCKER_README.md)**.

---

## API Overview

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/health` | — | Health check |
| POST | `/signup` | — | Register user |
| POST | `/login` | — | Login, returns JWT |
| GET | `/events` | — | List events (query: `city`, `date`) |
| GET | `/events/:id` | — | Get event by ID |
| GET | `/events/:id/seats` | — | Get seat map |
| POST | `/events` | JWT (admin) | Create event |
| GET | `/events/:id/lock-status` | JWT | Lock status + TTL |
| POST | `/events/:id/lock-seat` | JWT | Lock seats (body: `seatNumbers`) |
| POST | `/events/:id/create-booking` | JWT | Create pending booking |
| POST | `/events/:id/book` | JWT | Legacy: book without payment |
| POST | `/payments/pay` | JWT | Pay (body: `bookingId`; optional `simulateFailure`) |

---

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | `8080` | HTTP server port |
| `MYSQL_HOST` | `localhost` | MySQL host |
| `MYSQL_PORT` | `3306` | MySQL port |
| `MYSQL_DB` | `ticketdb` | MySQL database |
| `MYSQL_USER` | — | MySQL user (required) |
| `MYSQL_PASSWORD` | — | MySQL password (required) |
| `MONGO_URI` | `mongodb://localhost:27017` | MongoDB connection string |
| `MONGO_DB` | `ticketdb` | MongoDB database name |
| `REDIS_URI` | `redis://localhost:6379` | Redis connection string |
| `JWT_SECRET` | (dev default) | JWT signing secret |
| `ADMIN_EMAILS` | — | Comma-separated emails with admin role |

---
