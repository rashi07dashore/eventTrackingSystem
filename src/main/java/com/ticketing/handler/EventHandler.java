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

    public void getEvents(RoutingContext ctx) {
        mongoClient.find("events", new JsonObject(), res -> {
            if (res.succeeded()) {
                ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end(Json.encode(res.result()));
            }
        });
    }

    public void getEventById(RoutingContext ctx) {
        String id = ctx.pathParam("id");

        mongoClient.findOne("events",
                new JsonObject().put("_id", id),
                null,
                res -> {
                    if (res.succeeded()) {
                        ctx.response().end(res.result().encode());
                    }
                });
    }
}