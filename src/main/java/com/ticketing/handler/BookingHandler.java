package com.ticketing.handler;

import io.vertx.ext.web.RoutingContext;
import io.vertx.redis.client.*;
import io.vertx.mysqlclient.MySQLPool;
import io.vertx.sqlclient.Tuple;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.*;

public class BookingHandler {

    private final Redis redis;
    private final MySQLPool mysqlPool;
    private final MongoClient mongoClient;

    public BookingHandler(Redis redis,
                          MySQLPool mysqlPool,
                          MongoClient mongoClient) {
        this.redis = redis;
        this.mysqlPool = mysqlPool;
        this.mongoClient = mongoClient;
    }

    public void lockSeat(RoutingContext ctx) {

        String eventId = ctx.pathParam("id");
        JsonArray seats = ctx.getBodyAsJson().getJsonArray("seatNumbers");

        redis.connect(ar -> {
            RedisAPI api = RedisAPI.api(ar.result());

            for (Object seat : seats) {
                String key = "seat_lock:" + eventId + ":" + seat;
                api.set(Arrays.asList(key, "locked", "NX", "EX", "300"), r -> {});
            }

            ctx.response().end("Seats Locked");
        });
    }

    public void bookSeat(RoutingContext ctx) {

        String eventId = ctx.pathParam("id");
        JsonObject body = ctx.getBodyAsJson();

        String userId = body.getString("userId");
        JsonArray seats = body.getJsonArray("seatNumbers");
        Integer amount = body.getInteger("totalAmount");

        mysqlPool.preparedQuery(
                        "INSERT INTO bookings (user_id,event_id,seat_numbers,total_amount,status) VALUES (?,?,?,?,?)")
                .execute(Tuple.of(userId, eventId, seats.encode(), amount, "CONFIRMED"),
                        ar -> {
                            if (ar.succeeded()) {

                                for (Object seat : seats) {

                                    mongoClient.updateCollection(
                                            "events",
                                            new JsonObject().put("_id", eventId),
                                            new JsonObject().put("$set",
                                                    new JsonObject().put("seatLayout." + seat, "BOOKED")),
                                            r -> {}
                                    );
                                }

                                ctx.response().end("Booking Confirmed");
                            }
                        });
    }
}