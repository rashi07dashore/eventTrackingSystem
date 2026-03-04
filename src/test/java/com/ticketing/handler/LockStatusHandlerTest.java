package com.ticketing.handler;

import io.vertx.ext.web.RoutingContext;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.RedisConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LockStatusHandlerTest {

    @Mock
    private Redis redis;

    @Mock
    private RoutingContext ctx;

    @Mock
    private io.vertx.core.http.HttpServerResponse response;

    private LockStatusHandler handler;

    @BeforeEach
    void setUp() {
        handler = new LockStatusHandler(redis);
        when(ctx.response()).thenReturn(response);
    }

    @Test
    void getLockStatus_returns400_whenEventIdMissing() {
        when(ctx.pathParam("id")).thenReturn(null);
        handler.getLockStatus(ctx);
        verify(response).setStatusCode(400);
        verify(response).end("Missing event id");
    }

    @Test
    void getLockStatus_returns400_whenEventIdBlank() {
        when(ctx.pathParam("id")).thenReturn("   ");
        handler.getLockStatus(ctx);
        verify(response).setStatusCode(400);
        verify(response).end("Missing event id");
    }

    @Test
    void getLockStatus_returns200_withEmptyLocksWhenNoKeys() {
        when(ctx.pathParam("id")).thenReturn("evt-1");
        handler.getLockStatus(ctx);
        ArgumentCaptor<io.vertx.core.Handler> connectCaptor = ArgumentCaptor.forClass(io.vertx.core.Handler.class);
        verify(redis).connect(connectCaptor.capture());
        var connection = mock(RedisConnection.class);
        var api = mock(RedisAPI.class);
        try (MockedStatic<RedisAPI> redisApiMock = Mockito.mockStatic(RedisAPI.class)) {
            redisApiMock.when(() -> RedisAPI.api(connection)).thenReturn(api);
            var asyncResult = mock(io.vertx.core.AsyncResult.class);
            when(asyncResult.succeeded()).thenReturn(true);
            when(asyncResult.result()).thenReturn(connection);
            connectCaptor.getValue().handle(asyncResult);
        }
        ArgumentCaptor<io.vertx.core.Handler> keysCaptor = ArgumentCaptor.forClass(io.vertx.core.Handler.class);
        verify(api).keys(anyString(), keysCaptor.capture());
        var keysAsyncResult = mock(io.vertx.core.AsyncResult.class);
        when(keysAsyncResult.succeeded()).thenReturn(true);
        when(keysAsyncResult.result()).thenReturn(List.of());
        keysCaptor.getValue().handle(keysAsyncResult);
        verify(response).putHeader("Content-Type", "application/json");
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).end(bodyCaptor.capture());
        assertTrue(bodyCaptor.getValue().contains("evt-1"));
        assertTrue(bodyCaptor.getValue().contains("locks"));
    }
}
