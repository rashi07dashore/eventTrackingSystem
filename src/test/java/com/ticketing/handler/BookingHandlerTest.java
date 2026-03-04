package com.ticketing.handler;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.mysqlclient.MySQLPool;
import io.vertx.redis.client.Redis;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingHandlerTest {

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

    private BookingHandler handler;

    @BeforeEach
    void setUp() {
        handler = new BookingHandler(redis, mysqlPool, mongoClient);
        when(ctx.response()).thenReturn(response);
    }

    @Test
    void lockSeat_returns400_whenEventIdMissing() {
        when(ctx.pathParam("id")).thenReturn(null);
        when(ctx.getBodyAsJson()).thenReturn(new JsonObject().put("seatNumbers", new JsonArray().add("A1")));
        var user = mock(io.vertx.ext.auth.User.class);
        when(ctx.user()).thenReturn(user);
        when(user.principal()).thenReturn(new JsonObject().put("userId", 1L));
        handler.lockSeat(ctx);
        verify(response).setStatusCode(400);
        verify(response).end("Missing event id");
    }

    @Test
    void lockSeat_returns400_whenBodyIsNull() {
        when(ctx.pathParam("id")).thenReturn("evt-1");
        when(ctx.getBodyAsJson()).thenReturn(null);
        handler.lockSeat(ctx);
        verify(response).setStatusCode(400);
        verify(response).end("Request body required");
    }

    @Test
    void lockSeat_returns400_whenSeatNumbersEmpty() {
        when(ctx.pathParam("id")).thenReturn("evt-1");
        when(ctx.getBodyAsJson()).thenReturn(new JsonObject().put("seatNumbers", new JsonArray()));
        var user = mock(io.vertx.ext.auth.User.class);
        when(ctx.user()).thenReturn(user);
        when(user.principal()).thenReturn(new JsonObject().put("userId", 1L));
        handler.lockSeat(ctx);
        verify(response).setStatusCode(400);
        verify(response).end("seatNumbers array required and non-empty");
    }

    @Test
    void lockSeat_returns401_whenUserIdMissing() {
        when(ctx.pathParam("id")).thenReturn("evt-1");
        when(ctx.getBodyAsJson()).thenReturn(new JsonObject().put("seatNumbers", new JsonArray().add("A1")));
        var user = mock(io.vertx.ext.auth.User.class);
        when(ctx.user()).thenReturn(user);
        when(user.principal()).thenReturn(new JsonObject().put("userId", (Object) null));
        handler.lockSeat(ctx);
        verify(response).setStatusCode(401);
        verify(response).end("Unauthorized");
    }

    @Test
    void createBooking_returns400_whenEventIdMissing() {
        when(ctx.pathParam("id")).thenReturn(null);
        when(ctx.getBodyAsJson()).thenReturn(new JsonObject().put("seatNumbers", new JsonArray().add("A1")));
        var user = mock(io.vertx.ext.auth.User.class);
        when(ctx.user()).thenReturn(user);
        when(user.principal()).thenReturn(new JsonObject().put("userId", 1L));
        handler.createBooking(ctx);
        verify(response).setStatusCode(400);
        verify(response).end("Missing event id");
    }

    @Test
    void createBooking_returns400_whenSeatNumbersEmpty() {
        when(ctx.pathParam("id")).thenReturn("evt-1");
        when(ctx.getBodyAsJson()).thenReturn(new JsonObject().put("seatNumbers", new JsonArray()));
        var user = mock(io.vertx.ext.auth.User.class);
        when(ctx.user()).thenReturn(user);
        when(user.principal()).thenReturn(new JsonObject().put("userId", 1L));
        handler.createBooking(ctx);
        verify(response).setStatusCode(400);
        verify(response).end("seatNumbers array required and non-empty");
    }

    @Test
    void bookSeat_returns400_whenEventIdMissing() {
        when(ctx.pathParam("id")).thenReturn(null);
        when(ctx.getBodyAsJson()).thenReturn(new JsonObject().put("seatNumbers", new JsonArray().add("A1")));
        var user = mock(io.vertx.ext.auth.User.class);
        when(ctx.user()).thenReturn(user);
        when(user.principal()).thenReturn(new JsonObject().put("userId", 1L));
        handler.bookSeat(ctx);
        verify(response).setStatusCode(400);
        verify(response).end("Missing event id");
    }
}
