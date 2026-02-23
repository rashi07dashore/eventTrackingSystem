package com.ticketing.handler;

import io.vertx.ext.web.RoutingContext;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.RedisConnection;

import java.util.Arrays;

/**
 * Redis-based rate limit: max 5 lock attempts per minute per user.
 * Use as handler before lock-seat.
 */
public class RateLimitHandler {

    private static final String KEY_PREFIX = "rate_limit:lock:";
    private static final int MAX_PER_MINUTE = 5;
    private static final int WINDOW_SECONDS = 60;

    private final Redis redis;

    public RateLimitHandler(Redis redis) {
        this.redis = redis;
    }

    public void handle(RoutingContext ctx) {
        Long userId = ctx.user() != null && ctx.user().principal() != null
                ? ctx.user().principal().getLong("userId")
                : null;
        if (userId == null) {
            ctx.next();
            return;
        }

        String key = KEY_PREFIX + userId;
        redis.connect(conn -> {
            if (conn.failed()) {
                ctx.next();
                return;
            }
            RedisConnection connection = conn.result();
            RedisAPI api = RedisAPI.api(connection);
            api.incr(key).onComplete(ar -> {
                if (ar.failed()) {
                    connection.close();
                    ctx.next();
                    return;
                }
                Long count = ar.result() != null ? ar.result().toLong() : 1L;
                if (count == 1) {
                    api.expire(Arrays.asList(key, String.valueOf(WINDOW_SECONDS))).onComplete(expRes -> connection.close());
                } else {
                    connection.close();
                }
                if (count > MAX_PER_MINUTE) {
                    ctx.response()
                            .setStatusCode(429)
                            .putHeader("Content-Type", "application/json")
                            .end(new io.vertx.core.json.JsonObject()
                                    .put("error", "Too many lock attempts")
                                    .put("retryAfterSeconds", WINDOW_SECONDS).encode());
                    return;
                }
                ctx.next();
            });
        });
    }
}
