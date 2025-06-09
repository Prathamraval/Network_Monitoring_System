package org.nms.service;

import io.vertx.core.Vertx;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Tuple;
import io.vertx.sqlclient.RowSet;
import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import org.nms.Bootstrap;
import org.nms.utils.ConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatabaseService
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseService.class);
    private static DatabaseService instance;
    private static Pool pool;
    private static Vertx vertx;

    private DatabaseService()
    {
        vertx = Bootstrap.getVertx();
    }
    public static  DatabaseService getInstance()
    {
        if (instance == null)
        {
            instance = new DatabaseService();
            connect(Bootstrap.getVertx());
        }
        return instance;
    }

    public static void connect(Vertx vertx)
    {
        if (pool != null)
        {
            LOGGER.warn("DatabaseService is already connected.");
            return;
        }

        var connectOptions = new PgConnectOptions()
                .setPort(ConfigLoader.get().getInteger("database.port"))
                .setHost(ConfigLoader.get().getString("database.host"))
                .setDatabase(ConfigLoader.get().getString("database.name"))
                .setUser(ConfigLoader.get().getString("database.user"))
                .setPassword(ConfigLoader.get().getString("database.password"));

        var poolOptions = new PoolOptions().setMaxSize(5);
        pool = PgPool.pool(vertx, connectOptions, poolOptions);
        LOGGER.info("Database connection established.");
    }

    public Future<RowSet<Row>> executeQuery(String query)
    {
        return pool.query(query).execute();
    }
    public Future<RowSet<Row>> executePreparedQuery(String query, Tuple params)
    {
        return pool.preparedQuery(query).execute(params);
    }
}