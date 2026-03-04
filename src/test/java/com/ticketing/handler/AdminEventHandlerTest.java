package com.ticketing.handler;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminEventHandlerTest {

    @Mock
    private MongoClient mongoClient;

    @Mock
    private RoutingContext ctx;

    private AdminEventHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AdminEventHandler(mongoClient);
    }

    @Test
    void isAdmin_returnsFalse_whenUserIsNull() {
        when(ctx.user()).thenReturn(null);
        assertFalse(AdminEventHandler.isAdmin(ctx));
    }

    @Test
    void isAdmin_returnsFalse_whenPrincipalIsNull() {
        var user = mock(io.vertx.ext.auth.User.class);
        when(ctx.user()).thenReturn(user);
        when(user.principal()).thenReturn(null);
        assertFalse(AdminEventHandler.isAdmin(ctx));
    }

    @Test
    void isAdmin_returnsFalse_whenRoleIsNotAdmin() {
        var user = mock(io.vertx.ext.auth.User.class);
        var principal = new JsonObject().put("role", "user");
        when(ctx.user()).thenReturn(user);
        when(user.principal()).thenReturn(principal);
        assertFalse(AdminEventHandler.isAdmin(ctx));
    }

    @Test
    void isAdmin_returnsTrue_whenRoleIsAdmin() {
        var user = mock(io.vertx.ext.auth.User.class);
        var principal = new JsonObject().put("role", "admin");
        when(ctx.user()).thenReturn(user);
        when(user.principal()).thenReturn(principal);
        assertTrue(AdminEventHandler.isAdmin(ctx));
    }

    @Test
    void createEvent_returns403_whenNotAdmin() {
        var user = mock(io.vertx.ext.auth.User.class);
        when(ctx.user()).thenReturn(user);
        when(user.principal()).thenReturn(new JsonObject().put("role", "user"));
        var response = mock(io.vertx.core.http.HttpServerResponse.class);
        when(ctx.response()).thenReturn(response);

        handler.createEvent(ctx);

        verify(response).setStatusCode(403);
        verify(response).end("Admin only");
    }

    @Test
    void createEvent_returns400_whenBodyIsNull() {
        var user = mock(io.vertx.ext.auth.User.class);
        when(ctx.user()).thenReturn(user);
        when(user.principal()).thenReturn(new JsonObject().put("role", "admin"));
        when(ctx.getBodyAsJson()).thenReturn(null);
        var response = mock(io.vertx.core.http.HttpServerResponse.class);
        when(ctx.response()).thenReturn(response);

        handler.createEvent(ctx);

        verify(response).setStatusCode(400);
        verify(response).end("Request body required");
    }

    @Test
    void createEvent_returns400_whenNameMissing() {
        var user = mock(io.vertx.ext.auth.User.class);
        when(ctx.user()).thenReturn(user);
        when(user.principal()).thenReturn(new JsonObject().put("role", "admin"));
        when(ctx.getBodyAsJson()).thenReturn(new JsonObject()
                .put("location", "Hall A")
                .put("seats", new io.vertx.core.json.JsonArray().add(new JsonObject().put("seatNumber", "A1").put("category", "VIP").put("price", 100))));
        var response = mock(io.vertx.core.http.HttpServerResponse.class);
        when(ctx.response()).thenReturn(response);

        handler.createEvent(ctx);

        verify(response).setStatusCode(400);
        verify(response).end("name and location required");
    }

    @Test
    void createEvent_returns400_whenSeatsEmpty() {
        var user = mock(io.vertx.ext.auth.User.class);
        when(ctx.user()).thenReturn(user);
        when(user.principal()).thenReturn(new JsonObject().put("role", "admin"));
        when(ctx.getBodyAsJson()).thenReturn(new JsonObject()
                .put("name", "Concert")
                .put("location", "Hall A")
                .put("seats", new io.vertx.core.json.JsonArray()));
        var response = mock(io.vertx.core.http.HttpServerResponse.class);
        when(ctx.response()).thenReturn(response);

        handler.createEvent(ctx);

        verify(response).setStatusCode(400);
        verify(response).end("seats array required and non-empty");
    }
}
