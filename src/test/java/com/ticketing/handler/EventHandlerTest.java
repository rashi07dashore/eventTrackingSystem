package com.ticketing.handler;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;
import io.vertx.core.http.HttpServerRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventHandlerTest {

    @Mock
    private MongoClient mongoClient;

    @Mock
    private RoutingContext ctx;

    @Mock
    private io.vertx.core.http.HttpServerResponse response;

    @Mock
    private HttpServerRequest request;

    private EventHandler handler;

    @BeforeEach
    void setUp() {
        handler = new EventHandler(mongoClient);
        when(ctx.response()).thenReturn(response);
        when(ctx.request()).thenReturn(request);
    }

    @Test
    void getEventSeats_returns400_whenEventIdMissing() {
        when(ctx.pathParam("id")).thenReturn(null);
        handler.getEventSeats(ctx);
        verify(response).setStatusCode(400);
        verify(response).end("Missing event id");
    }

    @Test
    void getEventSeats_returns400_whenEventIdBlank() {
        when(ctx.pathParam("id")).thenReturn("   ");
        handler.getEventSeats(ctx);
        verify(response).setStatusCode(400);
        verify(response).end("Missing event id");
    }

    @Test
    void getEventSeats_returns404_whenEventNotFound() {
        when(ctx.pathParam("id")).thenReturn("evt-1");
        handler.getEventSeats(ctx);
        ArgumentCaptor<io.vertx.core.Handler> captor = ArgumentCaptor.forClass(io.vertx.core.Handler.class);
        verify(mongoClient).findOne(eq("events"), any(JsonObject.class), any(JsonObject.class), captor.capture());
        var asyncResult = mock(io.vertx.core.AsyncResult.class);
        when(asyncResult.succeeded()).thenReturn(true);
        when(asyncResult.result()).thenReturn(null);
        captor.getValue().handle(asyncResult);
        verify(response).setStatusCode(404);
        verify(response).end("Event Not Found");
    }

    @Test
    void getEventSeats_returns200_withSeatsWhenFound() {
        when(ctx.pathParam("id")).thenReturn("evt-1");
        var seats = new JsonArray().add(new JsonObject().put("seatNumber", "A1").put("status", "AVAILABLE"));
        var doc = new JsonObject().put("seats", seats);
        handler.getEventSeats(ctx);
        ArgumentCaptor<io.vertx.core.Handler> captor = ArgumentCaptor.forClass(io.vertx.core.Handler.class);
        verify(mongoClient).findOne(eq("events"), any(JsonObject.class), any(JsonObject.class), captor.capture());
        var asyncResult = mock(io.vertx.core.AsyncResult.class);
        when(asyncResult.succeeded()).thenReturn(true);
        when(asyncResult.result()).thenReturn(doc);
        captor.getValue().handle(asyncResult);
        verify(response).putHeader("Content-Type", "application/json");
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).end(bodyCaptor.capture());
        assertTrue(bodyCaptor.getValue().contains("evt-1"));
        assertTrue(bodyCaptor.getValue().contains("A1"));
    }

    @Test
    void getEvents_buildsQueryWithCityAndDate() {
        when(request.getParam("city")).thenReturn("Mumbai");
        when(request.getParam("date")).thenReturn("2025-03-15");
        handler.getEvents(ctx);
        ArgumentCaptor<JsonObject> queryCaptor = ArgumentCaptor.forClass(JsonObject.class);
        verify(mongoClient).find(eq("events"), queryCaptor.capture(), any(io.vertx.core.Handler.class));
        JsonObject query = queryCaptor.getValue();
        assertEquals("Mumbai", query.getString("city"));
        assertEquals("2025-03-15", query.getString("date"));
    }
}
