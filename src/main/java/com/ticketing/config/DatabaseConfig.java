package com.ticketing.config;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.mysqlclient.MySQLConnectOptions;
import io.vertx.mysqlclient.MySQLPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisOptions;
import io.vertx.ext.mongo.MongoClient;

public class DatabaseConfig {

    public static MySQLPool createMySQLPool(Vertx vertx) {

        MySQLConnectOptions options = new MySQLConnectOptions()
                .setHost(AppConfig.get("MYSQL_HOST"))
                .setPort(Integer.parseInt(AppConfig.get("MYSQL_PORT")))
                .setDatabase(AppConfig.get("MYSQL_DB"))
                .setUser(AppConfig.get("MYSQL_USER"))
                .setPassword(AppConfig.get("MYSQL_PASSWORD"));

        PoolOptions poolOptions = new PoolOptions().setMaxSize(10);

        return MySQLPool.pool(vertx, options, poolOptions);
    }

    public static MongoClient createMongoClient(Vertx vertx) {

        JsonObject config = new JsonObject()
                .put("connection_string", AppConfig.get("MONGO_URI"))
                .put("db_name", AppConfig.get("MONGO_DB"));

        return MongoClient.createShared(vertx, config);
    }

    public static Redis createRedisClient(Vertx vertx) {

        RedisOptions options = new RedisOptions()
                .setConnectionString(AppConfig.get("REDIS_URI"));

        return Redis.createClient(vertx, options);
    }
}