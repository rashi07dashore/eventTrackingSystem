package com.ticketing.handler;

import com.ticketing.utils.QueryUtils;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;
import io.vertx.mysqlclient.MySQLPool;
import io.vertx.redis.client.*;
import io.vertx.sqlclient.Tuple;
import io.vertx.core.CompositeFuture;
import io.vertx.sqlclient.SqlConnection;

import java.util.*;

public class BookingHandler {

    private static final int LOCK_TTL_SECONDS = 300;

    private final Redis redis;
    private final MySQLPool mysqlPool;
    private final MongoClient mongoClient;

    public BookingHandler(Redis redis, MySQLPool mysqlPool, MongoClient mongoClient) {
        this.redis = redis;
        this.mysqlPool = mysqlPool;
        this.mongoClient = mongoClient;
    }

    private static void badRequest(RoutingContext ctx, String message) {
        if (!ctx.response().ended()) {
            ctx.response().setStatusCode(400).end(message);
        }
    }

    private static void endOnce(RoutingContext ctx, int status, String body) {
        if (!ctx.response().ended()) {
            ctx.response().setStatusCode(status).end(body);
        }
    }

    // =========================
    // LOCK SEAT
    // =========================
    public void lockSeat(RoutingContext ctx) {

        String eventId = ctx.pathParam("id");
        if (eventId == null || eventId.isBlank()) {
            badRequest(ctx, "Missing event id");
            return;
        }

        JsonObject body = ctx.getBodyAsJson();
        if (body == null) {
            badRequest(ctx, "Request body required");
            return;
        }

        JsonArray seats = body.getJsonArray("seatNumbers");
        if (seats == null || seats.isEmpty()) {
            badRequest(ctx, "seatNumbers array required and non-empty");
            return;
        }

        Long userId = ctx.user().principal().getLong("userId");
        if (userId == null) {
            endOnce(ctx, 401, "Unauthorized");
            return;
        }

        redis.connect(redisConn -> {
            if (redisConn.failed()) {
                endOnce(ctx, 500, "Redis Error");
                return;
            }

            RedisConnection connection = redisConn.result();
            RedisAPI api = RedisAPI.api(connection);

            mongoClient.findOne("events",
                    new JsonObject().put("_id", eventId),
                    null,
                    mongoRes -> {

                        if (mongoRes.failed()) {
                            connection.close();
                            endOnce(ctx, 500, "Event lookup failed");
                            return;
                        }
                        if (mongoRes.result() == null) {
                            connection.close();
                            endOnce(ctx, 404, "Event Not Found");
                            return;
                        }

                        JsonObject event = mongoRes.result();
                        JsonArray eventSeats = event.getJsonArray("seats");
                        if (eventSeats == null) {
                            connection.close();
                            endOnce(ctx, 400, "Event has no seats");
                            return;
                        }

                        // Check all requested seats are available in Mongo
                        for (Object seatObj : seats) {
                            String seat = seatObj.toString();
                            boolean foundAvailable = false;
                            for (Object s : eventSeats) {
                                JsonObject seatJson = (JsonObject) s;
                                if (seatJson.getString("seatNumber").equals(seat)) {
                                    if ("BOOKED".equals(seatJson.getString("status"))) {
                                        connection.close();
                                        endOnce(ctx, 409, "Seat already booked: " + seat);
                                        return;
                                    }
                                    foundAvailable = true;
                                    break;
                                }
                            }
                            if (!foundAvailable) {
                                connection.close();
                                endOnce(ctx, 400, "Seat not found: " + seat);
                                return;
                            }
                        }

                        // Acquire all locks; collect keys we locked for rollback
                        List<Future> lockFutures = new ArrayList<>();
                        List<String> lockedKeys = new ArrayList<>();

                        for (Object seatObj : seats) {
                            String seat = seatObj.toString();
                            String key = "seat_lock:" + eventId + ":" + seat;

                            Future<Response> setFuture = api.set(Arrays.asList(key, userId.toString(), "NX", "EX", String.valueOf(LOCK_TTL_SECONDS)));
                            lockFutures.add(setFuture.onComplete(ar -> {
                                if (ar.succeeded() && ar.result() != null && "OK".equals(ar.result().toString())) {
                                    lockedKeys.add(key);
                                }
                            }));
                        }

                        CompositeFuture.all(lockFutures).onComplete(ar -> {
                            if (ar.failed()) {
                                releaseLocks(api, lockedKeys);
                                connection.close();
                                endOnce(ctx, 500, "Redis Error");
                                return;
                            }

                            // Check if every SET returned OK (we have same count as requested seats)
                            if (lockedKeys.size() != seats.size()) {
                                releaseLocks(api, lockedKeys);
                                connection.close();
                                endOnce(ctx, 409, "Seat already locked by another user");
                                return;
                            }

                            // Set Mongo seat status to LOCKED for each locked seat
                            final int[] pending = { seats.size() };
                            for (Object seatObj : seats) {
                                String seat = seatObj.toString();
                                mongoClient.updateCollection(
                                        "events",
                                        new JsonObject().put("_id", eventId).put("seats.seatNumber", seat).put("seats.status", "AVAILABLE"),
                                        new JsonObject().put("$set", new JsonObject().put("seats.$.status", "LOCKED")),
                                        r -> {
                                            synchronized (pending) {
                                                pending[0]--;
                                                if (pending[0] <= 0) {
                                                    connection.close();
                                                    endOnce(ctx, 200, "Seats Locked");
                                                }
                                            }
                                        }
                                );
                            }
                        });
                    });
        });
    }

    private void releaseLocks(RedisAPI api, List<String> keys) {
        if (keys.isEmpty()) return;
        api.del(keys);
    }

    // =========================
    // CREATE BOOKING (PENDING) – then client calls POST /payments/pay
    // Total amount computed server-side from seat categories/prices.
    // =========================
    public void createBooking(RoutingContext ctx) {

        String eventId = ctx.pathParam("id");
        if (eventId == null || eventId.isBlank()) {
            badRequest(ctx, "Missing event id");
            return;
        }

        JsonObject body = ctx.getBodyAsJson();
        if (body == null) {
            badRequest(ctx, "Request body required");
            return;
        }

        JsonArray seats = body.getJsonArray("seatNumbers");
        if (seats == null || seats.isEmpty()) {
            badRequest(ctx, "seatNumbers array required and non-empty");
            return;
        }

        Long userId = ctx.user().principal().getLong("userId");
        if (userId == null) {
            endOnce(ctx, 401, "Unauthorized");
            return;
        }

        redis.connect(redisConn -> {
            if (redisConn.failed()) {
                endOnce(ctx, 500, "Redis Error");
                return;
            }
            RedisConnection connection = redisConn.result();
            RedisAPI api = RedisAPI.api(connection);

            List<Future> validationFutures = new ArrayList<>();
            for (Object seatObj : seats) {
                String seat = seatObj.toString();
                String key = "seat_lock:" + eventId + ":" + seat;
                Future<Void> future = api.get(key).compose(res -> {
                    if (res == null) return Future.failedFuture("Seat lock expired");
                    if (!res.toString().equals(userId.toString())) return Future.failedFuture("Seat locked by another user");
                    return Future.succeededFuture();
                });
                validationFutures.add(future);
            }

            CompositeFuture.all(validationFutures).onSuccess(v -> {
                mongoClient.findOne("events", new JsonObject().put("_id", eventId), null, mongoRes -> {
                    if (mongoRes.failed() || mongoRes.result() == null) {
                        connection.close();
                        endOnce(ctx, 404, "Event Not Found");
                        return;
                    }
                    JsonObject event = mongoRes.result();
                    JsonArray eventSeats = event.getJsonArray("seats");
                    if (eventSeats == null) {
                        connection.close();
                        endOnce(ctx, 400, "Event has no seats");
                        return;
                    }
                    int totalAmount = 0;
                    for (Object seatObj : seats) {
                        String seat = seatObj.toString();
                        for (Object s : eventSeats) {
                            JsonObject seatJson = (JsonObject) s;
                            if (seat.equals(seatJson.getString("seatNumber"))) {
                                Integer p = seatJson.getInteger("price");
                                totalAmount += (p != null ? p : 0);
                                break;
                            }
                        }
                    }
                    final int total = totalAmount;

                    mysqlPool.getConnection(connAr -> {
                        if (connAr.failed()) {
                            connection.close();
                            endOnce(ctx, 500, "Booking failed");
                            return;
                        }
                        final SqlConnection sqlConn = connAr.result();
                        sqlConn.preparedQuery(QueryUtils.INSERT_BOOKING)
                                .execute(Tuple.of(userId, eventId, seats.encode(), total, "PENDING", "PENDING"), insertRes -> {
                                    if (insertRes.failed()) {
                                        sqlConn.close();
                                        connection.close();
                                        endOnce(ctx, 500, "Booking failed");
                                        return;
                                    }
                                    sqlConn.preparedQuery("SELECT LAST_INSERT_ID() as id").execute(selRes -> {
                                        connection.close();
                                        sqlConn.close();
                                        if (selRes.failed() || selRes.result().size() == 0) {
                                            endOnce(ctx, 500, "Booking failed");
                                            return;
                                        }
                                        long bookingId = selRes.result().iterator().next().getLong("id");
                                        ctx.response()
                                                .putHeader("Content-Type", "application/json")
                                                .end(new JsonObject()
                                                        .put("bookingId", bookingId)
                                                        .put("totalAmount", total)
                                                        .put("message", "Call POST /payments/pay with this bookingId to complete payment").encode());
                                    });
                                });
                    });
                });
            }).onFailure(err -> {
                connection.close();
                if ("Seat locked by another user".equals(err.getMessage())) {
                    endOnce(ctx, 403, err.getMessage());
                } else {
                    endOnce(ctx, 409, err.getMessage() != null ? err.getMessage() : "Lock validation failed");
                }
            });
        });
    }

    // =========================
    // LEGACY: direct book (confirm immediately, no payment flow)
    // =========================
    public void bookSeat(RoutingContext ctx) {

        String eventId = ctx.pathParam("id");
        if (eventId == null || eventId.isBlank()) {
            badRequest(ctx, "Missing event id");
            return;
        }

        JsonObject body = ctx.getBodyAsJson();
        if (body == null) {
            badRequest(ctx, "Request body required");
            return;
        }

        JsonArray seats = body.getJsonArray("seatNumbers");
        if (seats == null || seats.isEmpty()) {
            badRequest(ctx, "seatNumbers array required and non-empty");
            return;
        }

        Long userId = ctx.user().principal().getLong("userId");
        if (userId == null) {
            endOnce(ctx, 401, "Unauthorized");
            return;
        }

        redis.connect(redisConn -> {

            if (redisConn.failed()) {
                endOnce(ctx, 500, "Redis Error");
                return;
            }

            RedisConnection connection = redisConn.result();
            RedisAPI api = RedisAPI.api(connection);

            List<Future> validationFutures = new ArrayList<>();

            for (Object seatObj : seats) {

                String seat = seatObj.toString();
                String key = "seat_lock:" + eventId + ":" + seat;

                Future<Void> future = api.get(key).compose(res -> {

                    if (res == null) {
                        return Future.failedFuture("Seat lock expired");
                    }

                    if (!res.toString().equals(userId.toString())) {
                        return Future.failedFuture("Seat locked by another user");
                    }

                    return Future.succeededFuture();
                });

                validationFutures.add(future);
            }

            CompositeFuture.all(validationFutures).onSuccess(v -> {
                mongoClient.findOne("events", new JsonObject().put("_id", eventId), null, mongoRes -> {
                    if (mongoRes.failed() || mongoRes.result() == null) {
                        connection.close();
                        endOnce(ctx, 404, "Event Not Found");
                        return;
                    }
                    JsonObject event = mongoRes.result();
                    JsonArray eventSeats = event.getJsonArray("seats");
                    if (eventSeats == null) {
                        connection.close();
                        endOnce(ctx, 400, "Event has no seats");
                        return;
                    }
                    int totalAmount = 0;
                    for (Object seatObj : seats) {
                        String seat = seatObj.toString();
                        for (Object s : eventSeats) {
                            JsonObject seatJson = (JsonObject) s;
                            if (seat.equals(seatJson.getString("seatNumber"))) {
                                Integer p = seatJson.getInteger("price");
                                totalAmount += (p != null ? p : 0);
                                break;
                            }
                        }
                    }

                    mysqlPool.preparedQuery(QueryUtils.INSERT_BOOKING)
                            .execute(Tuple.of(
                                    userId,
                                    eventId,
                                    seats.encode(),
                                    totalAmount,
                                    "CONFIRMED",
                                    "SUCCESS"
                            ), ar -> {

                                if (ar.failed()) {
                                    connection.close();
                                    endOnce(ctx, 500, "Booking failed");
                                    return;
                                }
                                List<String> keysToDel = new ArrayList<>();
                                for (Object seat : seats) {
                                    String seatStr = seat.toString();
                                    mongoClient.updateCollection(
                                            "events",
                                            new JsonObject().put("_id", eventId).put("seats.seatNumber", seatStr).put("seats.status", "LOCKED"),
                                            new JsonObject().put("$set", new JsonObject().put("seats.$.status", "BOOKED")),
                                            r -> {}
                                    );
                                    keysToDel.add("seat_lock:" + eventId + ":" + seatStr);
                                }
                                api.del(keysToDel);
                                connection.close();
                                endOnce(ctx, 200, "Booking Confirmed");
                            });
                });
            }).onFailure(err -> {
                connection.close();
                if ("Seat locked by another user".equals(err.getMessage())) {
                    endOnce(ctx, 403, err.getMessage());
                } else {
                    endOnce(ctx, 409, err.getMessage() != null ? err.getMessage() : "Lock validation failed");
                }
            });
        });
    }
}