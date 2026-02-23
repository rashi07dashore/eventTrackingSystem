package com.ticketing.handler;

import com.ticketing.config.AppConfig;
import com.ticketing.utils.QueryUtils;
import io.vertx.ext.web.RoutingContext;
import io.vertx.mysqlclient.MySQLPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.core.json.JsonObject;
import org.mindrot.jbcrypt.BCrypt;
import io.vertx.ext.auth.JWTOptions;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class AuthHandler {

    private static final Set<String> ADMIN_EMAILS = parseAdminEmails(AppConfig.get("ADMIN_EMAILS", ""));

    private static Set<String> parseAdminEmails(String value) {
        if (value == null || value.isBlank()) return Set.of();
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    private final MySQLPool mysqlPool;
    private final JWTAuth jwtAuth;

    public AuthHandler(MySQLPool mysqlPool, JWTAuth jwtAuth) {
        this.mysqlPool = mysqlPool;
        this.jwtAuth = jwtAuth;
    }

    public void signup(RoutingContext ctx) {
        var body = ctx.getBodyAsJson();
        if (body == null) {
            ctx.response().setStatusCode(400).end("Request body required");
            return;
        }
        String name = body.getString("name");
        String email = body.getString("email");
        String password = body.getString("password");
        if (name == null || name.isBlank() || email == null || email.isBlank() || password == null || password.isBlank()) {
            ctx.response().setStatusCode(400).end("name, email and password required");
            return;
        }

        String hashed = BCrypt.hashpw(password, BCrypt.gensalt());

        mysqlPool.preparedQuery(QueryUtils.INSERT_USER)
                .execute(Tuple.of(name, email, hashed), ar -> {
                    if (ar.succeeded()) {
                        ctx.response().end("User Created");
                    } else {
                        ctx.response().setStatusCode(500).end("Registration failed");
                    }
                });
    }

    public void login(RoutingContext ctx) {
        var body = ctx.getBodyAsJson();
        if (body == null) {
            ctx.response().setStatusCode(400).end("Request body required");
            return;
        }
        String email = body.getString("email");
        String password = body.getString("password");
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            ctx.response().setStatusCode(400).end("email and password required");
            return;
        }

        mysqlPool.preparedQuery(QueryUtils.GET_USER_BY_EMAIL)
                .execute(Tuple.of(email), ar -> {

                    if (ar.succeeded() && ar.result().size() > 0) {

                        Row row = ar.result().iterator().next();
                        String storedHash = row.getString("password_hash");

                        if (BCrypt.checkpw(password, storedHash)) {

                            String userEmail = row.getString("email");
                            JsonObject claims = new JsonObject()
                                    .put("userId", row.getLong("id"))
                                    .put("email", userEmail);
                            if (ADMIN_EMAILS.contains(userEmail)) {
                                claims.put("role", "admin");
                            }

                            String token = jwtAuth.generateToken(
                                    claims,
                                    new JWTOptions().setExpiresInMinutes(60)
                            );

                            ctx.response()
                                    .putHeader("Content-Type", "application/json")
                                    .end(new JsonObject().put("token", token).encode());
                        } else {
                            ctx.response().setStatusCode(401).end("Invalid Credentials");
                        }

                    } else {
                        ctx.response().setStatusCode(401).end("Invalid Credentials");
                    }
                });
    }
}