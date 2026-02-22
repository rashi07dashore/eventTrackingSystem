package com.ticketing;

import com.ticketing.config.AppConfig;
import com.ticketing.config.DatabaseConfig;
import com.ticketing.handler.*;
import io.vertx.core.*;
import io.vertx.ext.web.*;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.mysqlclient.MySQLPool;
import io.vertx.redis.client.Redis;
import io.vertx.ext.mongo.MongoClient;

public class MainVerticle extends AbstractVerticle {

    private MySQLPool mysqlPool;
    private MongoClient mongoClient;
    private Redis redis;

    @Override
    public void start() {

        // Initialize DB Clients
        mysqlPool = DatabaseConfig.createMySQLPool(vertx);
        mongoClient = DatabaseConfig.createMongoClient(vertx);
        redis = DatabaseConfig.createRedisClient(vertx);

        // Create Router
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());

        // Initialize Handlers
        AuthHandler authHandler = new AuthHandler(mysqlPool);
        EventHandler eventHandler = new EventHandler(mongoClient);
        BookingHandler bookingHandler = new BookingHandler(redis, mysqlPool, mongoClient);

        // Routes
        router.post("/signup").handler(authHandler::signup);
        router.post("/login").handler(authHandler::login);

        router.get("/events").handler(eventHandler::getEvents);
        router.get("/events/:id").handler(eventHandler::getEventById);

        router.post("/events/:id/lock-seat").handler(bookingHandler::lockSeat);
        router.post("/events/:id/book").handler(bookingHandler::bookSeat);

        // Starting Server once
        int port = Integer.parseInt(AppConfig.get("SERVER_PORT"));
        vertx.createHttpServer()
                .requestHandler(router)
                .listen(port, res -> {
                    if (res.succeeded()) {
                        System.out.println("Server started on port " + port);
                    } else {
                        System.out.println("Failed to start server");
                    }
                });
    }

    public static void main(String[] args) {
        Vertx.vertx().deployVerticle(new MainVerticle());
    }
}