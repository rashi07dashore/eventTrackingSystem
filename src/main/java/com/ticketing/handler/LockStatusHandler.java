package com.ticketing.handler;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.RedisConnection;

import java.util.ArrayList;
import java.util.List;

/**
 * GET /events/:id/lock-status
 * Returns which seats are locked, by whom, and remaining TTL (seconds).
 */
public class LockStatusHandler {

    private static final String LOCK_KEY_PREFIX = "seat_lock:";

    private final Redis redis;

    public LockStatusHandler(Redis redis) {
        this.redis = redis;
    }

    public void getLockStatus(RoutingContext ctx) {
        String eventId = ctx.pathParam("id");
        if (eventId == null || eventId.isBlank()) {
            ctx.response().setStatusCode(400).end("Missing event id");
            return;
        }

        String pattern = LOCK_KEY_PREFIX + eventId + ":*";
        redis.connect(conn -> {
            if (conn.failed()) {
                ctx.response().setStatusCode(500).end("Redis Error");
                return;
            }
            RedisConnection connection = conn.result();
            RedisAPI api = RedisAPI.api(connection);

            api.keys(pattern).onComplete(ar -> {
                if (ar.failed()) {
                    connection.close();
                    ctx.response().setStatusCode(500).end("Redis Error");
                    return;
                }

                List<String> keys = new ArrayList<>();
                if (ar.result() != null) {
                    for (Object k : ar.result()) {
                        if (k != null) keys.add(k.toString());
                    }
                }

                if (keys.isEmpty()) {
                    connection.close();
                    ctx.response()
                            .putHeader("Content-Type", "application/json")
                            .end(new JsonObject()
                                    .put("eventId", eventId)
                                    .put("locks", new JsonArray()).encode());
                    return;
                }

                List<Future> lockFutures = new ArrayList<>();
                for (String key : keys) {
                    String seatNumber = key.substring(key.lastIndexOf(':') + 1);
                    io.vertx.core.Future<JsonObject> lockFuture = api.get(key)
                            .compose(v -> api.ttl(key).map(ttl -> {
                                long sec = (ttl != null && ttl.toLong() > 0) ? ttl.toLong() : 0;
                                return new JsonObject()
                                        .put("seatNumber", seatNumber)
                                        .put("lockedByUserId", v != null ? v.toString() : null)
                                        .put("remainingSeconds", sec);
                            }));
                    lockFutures.add(lockFuture);
                }

                io.vertx.core.CompositeFuture.all(lockFutures).onComplete(_ar -> {
                    connection.close();
                    JsonArray locks = new JsonArray();
                    if (_ar.succeeded()) {
                        for (Object o : _ar.result().list()) {
                            if (o instanceof JsonObject) locks.add((JsonObject) o);
                        }
                    }
                    ctx.response()
                            .putHeader("Content-Type", "application/json")
                            .end(new JsonObject()
                                    .put("eventId", eventId)
                                    .put("locks", locks).encode());
                });
            });
        });
    }
}
