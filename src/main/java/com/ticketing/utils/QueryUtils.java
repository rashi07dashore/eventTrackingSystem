package com.ticketing.utils;

public final class QueryUtils {

    private QueryUtils() {}

    /* ================= USERS ================= */

    public static final String INSERT_USER =
            "INSERT INTO users (name, email, password_hash) VALUES (?, ?, ?)";

    public static final String GET_USER_BY_EMAIL =
            "SELECT id, name, email, password_hash FROM users WHERE email = ?";

    public static final String GET_USER_BY_ID =
            "SELECT id, name, email FROM users WHERE id = ?";


    /* ================= BOOKINGS ================= */

    public static final String INSERT_BOOKING =
            "INSERT INTO bookings (user_id, event_id, seat_numbers, total_amount, status, payment_status) " +
                    "VALUES (?, ?, ?, ?, ?, ?)";

    public static final String GET_BOOKING_BY_ID =
            "SELECT * FROM bookings WHERE id = ?";

    public static final String GET_BOOKINGS_BY_USER =
            "SELECT * FROM bookings WHERE user_id = ?";

    public static final String UPDATE_BOOKING_STATUS =
            "UPDATE bookings SET status = ? WHERE id = ?";

    public static final String UPDATE_BOOKING_STATUS_AND_PAYMENT =
            "UPDATE bookings SET status = ?, payment_status = ? WHERE id = ?";


    /* ================= GENERIC ================= */

    public static final String DELETE_BOOKING =
            "DELETE FROM bookings WHERE id = ?";
}