package com.ticketing.handler;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.mysqlclient.MySQLPool;
import io.vertx.redis.client.Redis;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowIterator;
import io.vertx.sqlclient.RowSet;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentHandlerTest {

    @Mock
    private Redis redis;

    @Mock
    private MySQLPool mysqlPool;

    @Mock
    private MongoClient mongoClient;

    @Mock
    private RoutingContext ctx;

    @Mock
    private io.vertx.core.http.HttpServerResponse response;

    private PaymentHandler handler;

    @BeforeEach
    void setUp() {
        handler = new PaymentHandler(redis, mysqlPool, mongoClient);
        when(ctx.response()).thenReturn(response);
    }

    @Test
    void pay_returns400_whenBodyIsNull() {
        when(ctx.getBodyAsJson()).thenReturn(null);
        handler.pay(ctx);
        verify(response).setStatusCode(400);
        verify(response).end("Request body required");
    }

    @Test
    void pay_returns400_whenBookingIdMissing() {
        when(ctx.getBodyAsJson()).thenReturn(new JsonObject());
        handler.pay(ctx);
        verify(response).setStatusCode(400);
        verify(response).end("bookingId required");
    }

    @Test
    void pay_returns400_whenBookingIdBlank() {
        when(ctx.getBodyAsJson()).thenReturn(new JsonObject().put("bookingId", "   "));
        handler.pay(ctx);
        verify(response).setStatusCode(400);
        verify(response).end("bookingId required");
    }

    @Test
    void pay_returns401_whenUserNotAuthenticated() {
        var body = new JsonObject().put("bookingId", "1");
        when(ctx.getBodyAsJson()).thenReturn(body);
        var user = mock(io.vertx.ext.auth.User.class);
        when(ctx.user()).thenReturn(user);
        when(user.principal()).thenReturn(new JsonObject().put("userId", (Object) null));
        handler.pay(ctx);
        verify(response).setStatusCode(401);
        verify(response).end("Unauthorized");
    }

    @Test
    void pay_returns400_whenBookingIdInvalid() {
        var body = new JsonObject().put("bookingId", "not-a-number");
        when(ctx.getBodyAsJson()).thenReturn(body);
        var user = mock(io.vertx.ext.auth.User.class);
        when(ctx.user()).thenReturn(user);
        when(user.principal()).thenReturn(new JsonObject().put("userId", 100L));
        handler.pay(ctx);
        verify(response).setStatusCode(400);
        verify(response).end("Invalid bookingId");
    }
}
