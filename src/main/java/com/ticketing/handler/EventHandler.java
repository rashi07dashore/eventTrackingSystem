package com.ticketing.handler;

import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.Json;

public class EventHandler {

    private final MongoClient mongoClient;

    public EventHandler(MongoClient mongoClient) {
        this.mongoClient = mongoClient;
    }

    /** GET /events?city=&date= – filter by city and date. */
    public void getEvents(RoutingContext ctx) {
        JsonObject query = new JsonObject();
        String city = ctx.request().getParam("city");
        String date = ctx.request().getParam("date");
        if (city != null && !city.isBlank()) {
            query.put("city", city);
        }
        if (date != null && !date.isBlank()) {
            query.put("date", date);
        }

        mongoClient.find("events", query, res -> {
            if (res.failed()) {
                ctx.response().setStatusCode(500).end("Failed to load events");
                return;
            }
            ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(Json.encode(res.result()));
        });
    }

    public void getEventById(RoutingContext ctx) {
        String id = ctx.pathParam("id");

        mongoClient.findOne("events",
                new JsonObject().put("_id", id),
                null,
                res -> {
                    if (res.failed()) {
                        ctx.response().setStatusCode(500).end("Failed to load event");
                        return;
                    }
                    if (res.result() == null) {
                        ctx.response().setStatusCode(404).end("Event Not Found");
                        return;
                    }
                    ctx.response()
                            .putHeader("Content-Type", "application/json")
                            .end(res.result().encode());
                });
    }

    /** GET /events/:id/seats – return seats with status AVAILABLE | LOCKED | BOOKED and category, price. */
    public void getEventSeats(RoutingContext ctx) {
        String id = ctx.pathParam("id");
        if (id == null || id.isBlank()) {
            ctx.response().setStatusCode(400).end("Missing event id");
            return;
        }

        mongoClient.findOne("events",
                new JsonObject().put("_id", id),
                new JsonObject().put("seats", 1).put("_id", 0),
                res -> {
                    if (res.failed()) {
                        ctx.response().setStatusCode(500).end("Failed to load event");
                        return;
                    }
                    if (res.result() == null) {
                        ctx.response().setStatusCode(404).end("Event Not Found");
                        return;
                    }
                    Object seats = res.result().getValue("seats");
                    ctx.response()
                            .putHeader("Content-Type", "application/json")
                            .end(new JsonObject().put("eventId", id).put("seats", seats != null ? seats : new io.vertx.core.json.JsonArray()).encode());
                });
    }
}