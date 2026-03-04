package com.ticketing.handler;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.mysqlclient.MySQLPool;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthHandlerTest {

    @Mock
    private MySQLPool mysqlPool;

    @Mock
    private JWTAuth jwtAuth;

    @Mock
    private RoutingContext ctx;

    @Mock
    private io.vertx.core.http.HttpServerResponse response;

    private AuthHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AuthHandler(mysqlPool, jwtAuth);
        when(ctx.response()).thenReturn(response);
    }

    @Test
    void signup_returns400_whenBodyIsNull() {
        when(ctx.getBodyAsJson()).thenReturn(null);
        handler.signup(ctx);
        verify(response).setStatusCode(400);
        verify(response).end("Request body required");
        verify(mysqlPool, never()).preparedQuery(anyString());
    }

    @Test
    void signup_returns400_whenNameMissing() {
        when(ctx.getBodyAsJson()).thenReturn(new JsonObject().put("email", "a@b.com").put("password", "pass"));
        handler.signup(ctx);
        verify(response).setStatusCode(400);
        verify(response).end("name, email and password required");
    }

    @Test
    void signup_returns400_whenEmailMissing() {
        when(ctx.getBodyAsJson()).thenReturn(new JsonObject().put("name", "Alice").put("password", "pass"));
        handler.signup(ctx);
        verify(response).setStatusCode(400);
        verify(response).end("name, email and password required");
    }

    @Test
    void signup_returns400_whenPasswordMissing() {
        when(ctx.getBodyAsJson()).thenReturn(new JsonObject().put("name", "Alice").put("email", "a@b.com"));
        handler.signup(ctx);
        verify(response).setStatusCode(400);
        verify(response).end("name, email and password required");
    }

    @Test
    void login_returns400_whenBodyIsNull() {
        when(ctx.getBodyAsJson()).thenReturn(null);
        handler.login(ctx);
        verify(response).setStatusCode(400);
        verify(response).end("Request body required");
    }

    @Test
    void login_returns400_whenEmailMissing() {
        when(ctx.getBodyAsJson()).thenReturn(new JsonObject().put("password", "pass"));
        handler.login(ctx);
        verify(response).setStatusCode(400);
        verify(response).end("email and password required");
    }

    @Test
    void login_returns400_whenPasswordMissing() {
        when(ctx.getBodyAsJson()).thenReturn(new JsonObject().put("email", "a@b.com"));
        handler.login(ctx);
        verify(response).setStatusCode(400);
        verify(response).end("email and password required");
    }
}
