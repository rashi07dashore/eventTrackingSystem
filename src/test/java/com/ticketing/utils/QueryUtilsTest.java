package com.ticketing.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QueryUtilsTest {

    @Test
    void insertUser_containsPlaceholders() {
        assertNotNull(QueryUtils.INSERT_USER);
        assertTrue(QueryUtils.INSERT_USER.contains("INSERT INTO users"));
        assertTrue(QueryUtils.INSERT_USER.contains("?"));
    }

    @Test
    void getUserByEmail_selectsByEmail() {
        assertNotNull(QueryUtils.GET_USER_BY_EMAIL);
        assertTrue(QueryUtils.GET_USER_BY_EMAIL.contains("email"));
        assertTrue(QueryUtils.GET_USER_BY_EMAIL.contains("?"));
    }

    @Test
    void insertBooking_hasRequiredColumns() {
        assertNotNull(QueryUtils.INSERT_BOOKING);
        assertTrue(QueryUtils.INSERT_BOOKING.contains("user_id"));
        assertTrue(QueryUtils.INSERT_BOOKING.contains("event_id"));
        assertTrue(QueryUtils.INSERT_BOOKING.contains("seat_numbers"));
        assertTrue(QueryUtils.INSERT_BOOKING.contains("total_amount"));
        assertTrue(QueryUtils.INSERT_BOOKING.contains("status"));
        assertTrue(QueryUtils.INSERT_BOOKING.contains("payment_status"));
    }

    @Test
    void getBookingById_selectsById() {
        assertNotNull(QueryUtils.GET_BOOKING_BY_ID);
        assertTrue(QueryUtils.GET_BOOKING_BY_ID.contains("id"));
    }

    @Test
    void updateBookingStatusAndPayment_updatesStatusAndPayment() {
        assertNotNull(QueryUtils.UPDATE_BOOKING_STATUS_AND_PAYMENT);
        assertTrue(QueryUtils.UPDATE_BOOKING_STATUS_AND_PAYMENT.contains("status"));
        assertTrue(QueryUtils.UPDATE_BOOKING_STATUS_AND_PAYMENT.contains("payment_status"));
    }
}
