package com.ticketing.handler;

import io.vertx.ext.web.RoutingContext;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitHandlerTest {

    @Mock
    private Redis redis;

    @Mock
    private RoutingContext ctx;

    @Mock
    private io.vertx.core.http.HttpServerResponse response;

    private RateLimitHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RateLimitHandler(redis);
        when(ctx.response()).thenReturn(response);
    }

    @Test
    void handle_callsNext_whenUserIdIsNull() {
        when(ctx.user()).thenReturn(null);
        handler.handle(ctx);
        verify(ctx).next();
        verify(redis, never()).connect(any());
    }

    @Test
    void handle_callsNext_whenPrincipalIsNull() {
        var user = mock(io.vertx.ext.auth.User.class);
        when(ctx.user()).thenReturn(user);
        when(user.principal()).thenReturn(null);
        handler.handle(ctx);
        verify(ctx).next();
    }

    @Test
    void handle_returns429_whenCountExceedsLimit() {
        var user = mock(io.vertx.ext.auth.User.class);
        when(ctx.user()).thenReturn(user);
        when(user.principal()).thenReturn(new io.vertx.core.json.JsonObject().put("userId", 42L));
        handler.handle(ctx);
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
        ArgumentCaptor<io.vertx.core.Handler> incrCaptor = ArgumentCaptor.forClass(io.vertx.core.Handler.class);
        verify(api).incr(anyString(), incrCaptor.capture());
        var incrResponse = mock(Response.class);
        when(incrResponse.toLong()).thenReturn(6L);
        var incrResult = mock(io.vertx.core.AsyncResult.class);
        when(incrResult.succeeded()).thenReturn(true);
        when(incrResult.result()).thenReturn(incrResponse);
        incrCaptor.getValue().handle(incrResult);
        verify(response).setStatusCode(429);
        verify(response).putHeader("Content-Type", "application/json");
    }
}
