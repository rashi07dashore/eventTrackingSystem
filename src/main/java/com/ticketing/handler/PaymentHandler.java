package com.ticketing.handler;

import com.ticketing.utils.QueryUtils;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;
import io.vertx.mysqlclient.MySQLPool;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.RedisConnection;
import io.vertx.sqlclient.Tuple;

import java.util.ArrayList;
import java.util.List;

/**
 * Payment simulation: POST /payments/pay { "bookingId": "..." }
 * Simulates success; on success confirms booking (MySQL + Mongo) and releases Redis locks.
 * On failure, releases locks and sets booking to FAILED.
 */
public class PaymentHandler {

    private final Redis redis;
    private final MySQLPool mysqlPool;
    private final MongoClient mongoClient;

    public PaymentHandler(Redis redis, MySQLPool mysqlPool, MongoClient mongoClient) {
        this.redis = redis;
        this.mysqlPool = mysqlPool;
        this.mongoClient = mongoClient;
    }

    public void pay(RoutingContext ctx) {
        JsonObject body = ctx.getBodyAsJson();
        if (body == null) {
            ctx.response().setStatusCode(400).end("Request body required");
            return;
        }
        Object bid = body.getValue("bookingId");
        if (bid == null || bid.toString().isBlank()) {
            ctx.response().setStatusCode(400).end("bookingId required");
            return;
        }
        Long userId = ctx.user().principal().getLong("userId");
        if (userId == null) {
            ctx.response().setStatusCode(401).end("Unauthorized");
            return;
        }

        String bookingIdStr = bid.toString();
        long bookingId;
        try {
            bookingId = Long.parseLong(bookingIdStr);
        } catch (NumberFormatException e) {
            ctx.response().setStatusCode(400).end("Invalid bookingId");
            return;
        }

        mysqlPool.preparedQuery(QueryUtils.GET_BOOKING_BY_ID)
                .execute(Tuple.of(bookingId), ar -> {
                    if (ar.failed() || ar.result().size() == 0) {
                        ctx.response().setStatusCode(404).end("Booking not found");
                        return;
                    }
                    var row = ar.result().iterator().next();
                    if (!userId.equals(row.getLong("user_id"))) {
                        ctx.response().setStatusCode(403).end("Not your booking");
                        return;
                    }
                    String status = row.getString("status");
                    if (!"PENDING".equals(status)) {
                        ctx.response().setStatusCode(400).end("Booking not pending payment");
                        return;
                    }
                    String eventId = row.getString("event_id");
                    String seatNumbersJson = row.getString("seat_numbers");
                    JsonArray seatNumbers = new JsonArray(seatNumbersJson != null ? seatNumbersJson : "[]");

                    // Simulate payment: always success for now; could add body "simulateFailure": true
                    boolean simulateFailure = Boolean.TRUE.equals(body.getBoolean("simulateFailure"));

                    if (simulateFailure) {
                        releaseLocksAndFailBooking(ctx, eventId, seatNumbers, bookingId);
                        return;
                    }

                    redis.connect(conn -> {
                        if (conn.failed()) {
                            ctx.response().setStatusCode(500).end("Redis Error");
                            return;
                        }
                        RedisConnection connection = conn.result();
                        RedisAPI api = RedisAPI.api(connection);

                        List<String> keysToDel = new ArrayList<>();
                        for (Object s : seatNumbers) {
                            keysToDel.add("seat_lock:" + eventId + ":" + s.toString());
                        }
                        api.del(keysToDel);

                        mysqlPool.preparedQuery(QueryUtils.UPDATE_BOOKING_STATUS_AND_PAYMENT)
                                .execute(Tuple.of("CONFIRMED", "SUCCESS", bookingId), up -> {
                                    connection.close();
                                    if (up.failed()) {
                                        ctx.response().setStatusCode(500).end("Failed to confirm booking");
                                        return;
                                    }
                                    for (Object s : seatNumbers) {
                                        String seatStr = s.toString();
                                        mongoClient.updateCollection(
                                                "events",
                                                new JsonObject().put("_id", eventId).put("seats.seatNumber", seatStr).put("seats.status", "LOCKED"),
                                                new JsonObject().put("$set", new JsonObject().put("seats.$.status", "BOOKED")),
                                                r -> {}
                                        );
                                    }
                                    ctx.response()
                                            .putHeader("Content-Type", "application/json")
                                            .end(new JsonObject()
                                                    .put("status", "SUCCESS")
                                                    .put("message", "Payment successful").encode());
                                });
                    });
                });
    }

    private void releaseLocksAndFailBooking(RoutingContext ctx, String eventId, JsonArray seatNumbers, long bookingId) {
        redis.connect(conn -> {
            if (conn.failed()) {
                ctx.response().setStatusCode(500).end("Redis Error");
                return;
            }
            RedisConnection connection = conn.result();
            RedisAPI api = RedisAPI.api(connection);
            List<String> keysToDel = new ArrayList<>();
            for (Object s : seatNumbers) {
                keysToDel.add("seat_lock:" + eventId + ":" + s.toString());
            }
            api.del(keysToDel);
            connection.close();

            mysqlPool.preparedQuery(QueryUtils.UPDATE_BOOKING_STATUS_AND_PAYMENT)
                    .execute(Tuple.of("FAILED", "FAILED", bookingId), up -> {
                        for (Object s : seatNumbers) {
                            String seatStr = s.toString();
                            mongoClient.updateCollection(
                                    "events",
                                    new JsonObject().put("_id", eventId).put("seats.seatNumber", seatStr).put("seats.status", "LOCKED"),
                                    new JsonObject().put("$set", new JsonObject().put("seats.$.status", "AVAILABLE")),
                                    r -> {}
                            );
                        }
                        ctx.response()
                                .setStatusCode(400)
                                .putHeader("Content-Type", "application/json")
                                .end(new JsonObject().put("status", "FAILED").put("message", "Payment failed").encode());
                    });
        });
    }
}
