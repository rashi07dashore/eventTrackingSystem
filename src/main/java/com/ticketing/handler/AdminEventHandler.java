package com.ticketing.handler;

import com.ticketing.config.AppConfig;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;

import java.util.UUID;

/**
 * Admin-only: create events with show timings and dynamic seat layout (category + price).
 */
public class AdminEventHandler {

    private final MongoClient mongoClient;

    public AdminEventHandler(MongoClient mongoClient) {
        this.mongoClient = mongoClient;
    }

    /** Check if current user has admin role (from JWT). */
    public static boolean isAdmin(RoutingContext ctx) {
        String role = ctx.user() != null && ctx.user().principal() != null
                ? ctx.user().principal().getString("role")
                : null;
        return "admin".equalsIgnoreCase(role);
    }

    /**
     * POST /events
     * Body: { name, location, city?, date?, showTimings: [{ startTime, endTime? }], seats: [{ seatNumber, category, price }] }
     */
    public void createEvent(RoutingContext ctx) {
        if (!isAdmin(ctx)) {
            ctx.response().setStatusCode(403).end("Admin only");
            return;
        }

        JsonObject body = ctx.getBodyAsJson();
        if (body == null) {
            ctx.response().setStatusCode(400).end("Request body required");
            return;
        }

        String name = body.getString("name");
        String location = body.getString("location");
        if (name == null || name.isBlank() || location == null || location.isBlank()) {
            ctx.response().setStatusCode(400).end("name and location required");
            return;
        }

        JsonArray showTimings = body.getJsonArray("showTimings");
        JsonArray seatsInput = body.getJsonArray("seats");
        if (seatsInput == null || seatsInput.isEmpty()) {
            ctx.response().setStatusCode(400).end("seats array required and non-empty");
            return;
        }

        JsonArray seats = new JsonArray();
        for (Object o : seatsInput) {
            JsonObject s = (JsonObject) o;
            String seatNumber = s.getString("seatNumber");
            String category = s.getString("category");
            Integer price = s.getInteger("price");
            if (seatNumber == null || seatNumber.isBlank()) continue;
            if (category == null) category = "STANDARD";
            if (price == null || price < 0) price = 0;
            seats.add(new JsonObject()
                    .put("seatNumber", seatNumber)
                    .put("category", category)
                    .put("price", price)
                    .put("status", "AVAILABLE"));
        }

        if (seats.isEmpty()) {
            ctx.response().setStatusCode(400).end("At least one valid seat required");
            return;
        }

        JsonArray showTimingsDoc = new JsonArray();
        if (showTimings != null) {
            for (Object o : showTimings) {
                JsonObject t = (JsonObject) o;
                String startTime = t.getString("startTime");
                if (startTime == null) continue;
                showTimingsDoc.add(new JsonObject()
                        .put("id", UUID.randomUUID().toString())
                        .put("startTime", startTime)
                        .put("endTime", t.getString("endTime")));
            }
        }

        String city = body.getString("city");
        String date = body.getString("date");

        JsonObject event = new JsonObject()
                .put("_id", UUID.randomUUID().toString())
                .put("name", name)
                .put("location", location)
                .put("city", city != null ? city : "")
                .put("date", date != null ? date : "")
                .put("showTimings", showTimingsDoc)
                .put("seats", seats);

        mongoClient.insert("events", event, ar -> {
            if (ar.failed()) {
                ctx.response().setStatusCode(500).end("Failed to create event");
                return;
            }
            ctx.response()
                    .setStatusCode(201)
                    .putHeader("Content-Type", "application/json")
                    .end(event.encode());
        });
    }
}
