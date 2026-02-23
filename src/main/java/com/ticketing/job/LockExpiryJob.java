package com.ticketing.job;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.RedisConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Periodic job: find seats with status LOCKED in Mongo whose Redis lock has expired (key missing),
 * and set those seats back to AVAILABLE.
 */
public class LockExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(LockExpiryJob.class);
    private static final String LOCK_KEY_PREFIX = "seat_lock:";

    private final Vertx vertx;
    private final MongoClient mongoClient;
    private final Redis redis;

    public LockExpiryJob(Vertx vertx, MongoClient mongoClient, Redis redis) {
        this.vertx = vertx;
        this.mongoClient = mongoClient;
        this.redis = redis;
    }

    /** Run every 60 seconds. */
    public void schedule() {
        vertx.setPeriodic(60_000, id -> run());
    }

    public void run() {
        mongoClient.find("events", new JsonObject(), ar -> {
            if (ar.failed()) {
                log.warn("LockExpiryJob: failed to list events");
                return;
            }
            for (JsonObject event : ar.result()) {
                String eventId = event.getString("_id");
                JsonArray seats = event.getJsonArray("seats");
                if (seats == null) continue;
                for (Object o : seats) {
                    JsonObject seat = (JsonObject) o;
                    if (!"LOCKED".equals(seat.getString("status"))) continue;
                    String seatNumber = seat.getString("seatNumber");
                    if (seatNumber == null) continue;
                    String key = LOCK_KEY_PREFIX + eventId + ":" + seatNumber;
                    redis.connect(conn -> {
                        if (conn.failed()) return;
                        RedisConnection c = conn.result();
                        RedisAPI api = RedisAPI.api(c);
                        api.exists(List.of(key)).onComplete(ex -> {
                            if (ex.succeeded() && ex.result() != null && ex.result().toLong() == 0) {
                                // Key does not exist -> lock expired, set seat back to AVAILABLE
                                mongoClient.updateCollection(
                                        "events",
                                        new JsonObject()
                                                .put("_id", eventId)
                                                .put("seats.seatNumber", seatNumber)
                                                .put("seats.status", "LOCKED"),
                                        new JsonObject().put("$set", new JsonObject().put("seats.$.status", "AVAILABLE")),
                                        up -> {
                                            if (up.succeeded()) {
                                                log.debug("Released expired lock: {} {}", eventId, seatNumber);
                                            }
                                        });
                            }
                            c.close();
                        });
                    });
                }
            }
        });
    }
}
