package com.ticketing;

import com.ticketing.config.AppConfig;
import com.ticketing.config.DatabaseConfig;
import com.ticketing.handler.*;
import com.ticketing.job.LockExpiryJob;
import io.vertx.core.*;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.web.*;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.JWTAuthHandler;
import io.vertx.mysqlclient.MySQLPool;
import io.vertx.redis.client.Redis;
import io.vertx.ext.mongo.MongoClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainVerticle extends AbstractVerticle {

    private static final Logger log = LoggerFactory.getLogger(MainVerticle.class);

    private MySQLPool mysqlPool;
    private MongoClient mongoClient;
    private Redis redis;

    @Override
    public void start() {

        // Initialize DB Clients
        mysqlPool = DatabaseConfig.createMySQLPool(vertx);
        mongoClient = DatabaseConfig.createMongoClient(vertx);
        redis = DatabaseConfig.createRedisClient(vertx);

        // Lock expiry job: release Mongo LOCKED seats when Redis key expired
        new LockExpiryJob(vertx, mongoClient, redis).schedule();

        // Create Router
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());

        // CORS
        router.route().handler(CorsHandler.create()
                .allowedMethod(io.vertx.core.http.HttpMethod.GET)
                .allowedMethod(io.vertx.core.http.HttpMethod.POST)
                .allowedMethod(io.vertx.core.http.HttpMethod.PUT)
                .allowedMethod(io.vertx.core.http.HttpMethod.DELETE)
                .allowedHeader("Content-Type")
                .allowedHeader("Authorization"));

        // Health check (no auth)
        router.get("/health").handler(ctx -> {
            ctx.response().putHeader("Content-Type", "application/json")
                    .end(new io.vertx.core.json.JsonObject().put("status", "UP").encode());
        });

        // JWT secret from env (required for production)
        String jwtSecret = AppConfig.get("JWT_SECRET");
        if (jwtSecret == null || jwtSecret.isBlank()) {
            log.warn("JWT_SECRET not set; using default for development only");
            jwtSecret = "change-me-in-production";
        }
        final String secret = jwtSecret;

        JWTAuth jwtAuth = JWTAuth.create(vertx, new JWTAuthOptions()
                .addPubSecKey(new PubSecKeyOptions()
                        .setAlgorithm("HS256")
                        .setBuffer(secret)));

        AuthHandler authHandler = new AuthHandler(mysqlPool, jwtAuth);
        EventHandler eventHandler = new EventHandler(mongoClient);
        AdminEventHandler adminEventHandler = new AdminEventHandler(mongoClient);
        BookingHandler bookingHandler = new BookingHandler(redis, mysqlPool, mongoClient);
        LockStatusHandler lockStatusHandler = new LockStatusHandler(redis);
        PaymentHandler paymentHandler = new PaymentHandler(redis, mysqlPool, mongoClient);
        RateLimitHandler rateLimitHandler = new RateLimitHandler(redis);

        // Routes
        router.post("/signup").handler(authHandler::signup);
        router.post("/login").handler(authHandler::login);

        router.get("/events").handler(eventHandler::getEvents);
        router.get("/events/:id").handler(eventHandler::getEventById);
        router.get("/events/:id/seats").handler(eventHandler::getEventSeats);

        // Admin only
        router.post("/events").handler(JWTAuthHandler.create(jwtAuth)).handler(adminEventHandler::createEvent);

        // Protected event routes (JWT required)
        router.route("/events/:id/*").handler(JWTAuthHandler.create(jwtAuth));
        router.get("/events/:id/lock-status").handler(lockStatusHandler::getLockStatus);
        router.post("/events/:id/lock-seat").handler(rateLimitHandler::handle).handler(bookingHandler::lockSeat);
        router.post("/events/:id/create-booking").handler(bookingHandler::createBooking);
        router.post("/events/:id/book").handler(bookingHandler::bookSeat);

        // Payment (JWT required)
        router.post("/payments/pay").handler(JWTAuthHandler.create(jwtAuth)).handler(paymentHandler::pay);

        int port = Integer.parseInt(AppConfig.get("SERVER_PORT", "8080"));
        vertx.createHttpServer()
                .requestHandler(router)
                .listen(port, res -> {
                    if (res.succeeded()) {
                        log.info("Server started on port {}", port);
                    } else {
                        log.error("Failed to start server", res.cause());
                        res.cause().printStackTrace();
                    }
                });
    }

    public static void main(String[] args) {
        Vertx.vertx().deployVerticle(new MainVerticle());
    }
}