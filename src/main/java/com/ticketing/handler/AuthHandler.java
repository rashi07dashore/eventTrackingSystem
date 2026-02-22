package com.ticketing.handler;

import io.vertx.ext.web.RoutingContext;
import io.vertx.mysqlclient.MySQLPool;
import io.vertx.sqlclient.Tuple;

public class AuthHandler {

    private final MySQLPool mysqlPool;

    public AuthHandler(MySQLPool mysqlPool) {
        this.mysqlPool = mysqlPool;
    }

    public void signup(RoutingContext ctx) {
        var body = ctx.getBodyAsJson();

        mysqlPool.preparedQuery(
                        "INSERT INTO users (name,email,password_hash) VALUES (?,?,?)")
                .execute(Tuple.of(
                        body.getString("name"),
                        body.getString("email"),
                        body.getString("password")
                ), ar -> {
                    if (ar.succeeded()) {
                        ctx.response().end("User Created");
                    } else {
                        ctx.response().setStatusCode(500).end(ar.cause().getMessage());
                    }
                });
    }

    public void login(RoutingContext ctx) {
        var body = ctx.getBodyAsJson();

        mysqlPool.preparedQuery(
                        "SELECT * FROM users WHERE email=? AND password_hash=?")
                .execute(Tuple.of(
                        body.getString("email"),
                        body.getString("password")
                ), ar -> {
                    if (ar.succeeded() && ar.result().size() > 0) {
                        ctx.response().end("Login Success");
                    } else {
                        ctx.response().setStatusCode(401).end("Invalid Credentials");
                    }
                });
    }
}