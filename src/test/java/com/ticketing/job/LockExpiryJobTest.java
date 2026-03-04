package com.ticketing.job;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.RedisConnection;
import io.vertx.redis.client.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LockExpiryJobTest {

    @Mock
    private Vertx vertx;

    @Mock
    private MongoClient mongoClient;

    @Mock
    private Redis redis;

    private LockExpiryJob job;

    @BeforeEach
    void setUp() {
        job = new LockExpiryJob(vertx, mongoClient, redis);
    }

    @Test
    void schedule_setsPeriodicTimer() {
        job.schedule();
        verify(vertx).setPeriodic(eq(60_000L), any());
    }

    @Test
    void run_findsEventsAndChecksLockedSeats() {
        job.run();
        ArgumentCaptor<io.vertx.core.Handler> findCaptor = ArgumentCaptor.forClass(io.vertx.core.Handler.class);
        verify(mongoClient).find(eq("events"), eq(new JsonObject()), findCaptor.capture());
        var event = new JsonObject()
                .put("_id", "evt-1")
                .put("seats", new JsonArray()
                        .add(new JsonObject().put("seatNumber", "A1").put("status", "LOCKED"))
                        .add(new JsonObject().put("seatNumber", "A2").put("status", "AVAILABLE")));
        var findResult = mock(io.vertx.core.AsyncResult.class);
        when(findResult.succeeded()).thenReturn(true);
        when(findResult.result()).thenReturn(List.of(event));
        findCaptor.getValue().handle(findResult);
        ArgumentCaptor<io.vertx.core.Handler> connectCaptor = ArgumentCaptor.forClass(io.vertx.core.Handler.class);
        verify(redis).connect(connectCaptor.capture());
        var connection = mock(RedisConnection.class);
        var api = mock(RedisAPI.class);
        try (MockedStatic<RedisAPI> redisApiMock = Mockito.mockStatic(RedisAPI.class)) {
            redisApiMock.when(() -> RedisAPI.api(connection)).thenReturn(api);
            var connResult = mock(io.vertx.core.AsyncResult.class);
            when(connResult.succeeded()).thenReturn(true);
            when(connResult.result()).thenReturn(connection);
            connectCaptor.getValue().handle(connResult);
        }
        ArgumentCaptor<io.vertx.core.Handler> existsCaptor = ArgumentCaptor.forClass(io.vertx.core.Handler.class);
        verify(api).exists(any(List.class), existsCaptor.capture());
        var existsResponse = mock(Response.class);
        when(existsResponse.toLong()).thenReturn(0L);
        var existsResult = mock(io.vertx.core.AsyncResult.class);
        when(existsResult.succeeded()).thenReturn(true);
        when(existsResult.result()).thenReturn(existsResponse);
        existsCaptor.getValue().handle(existsResult);
        verify(mongoClient).updateCollection(eq("events"), any(JsonObject.class), any(JsonObject.class), any());
    }

    @Test
    void run_skipsSeatsThatAreNotLocked() {
        job.run();
        ArgumentCaptor<io.vertx.core.Handler> findCaptor = ArgumentCaptor.forClass(io.vertx.core.Handler.class);
        verify(mongoClient).find(eq("events"), eq(new JsonObject()), findCaptor.capture());
        var event = new JsonObject()
                .put("_id", "evt-1")
                .put("seats", new JsonArray()
                        .add(new JsonObject().put("seatNumber", "A1").put("status", "AVAILABLE")));
        var findResult = mock(io.vertx.core.AsyncResult.class);
        when(findResult.succeeded()).thenReturn(true);
        when(findResult.result()).thenReturn(List.of(event));
        findCaptor.getValue().handle(findResult);
        verify(redis, never()).connect(any());
    }
}
