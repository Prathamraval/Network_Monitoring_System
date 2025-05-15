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
import org.nms.Main;
import org.nms.utils.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatabaseService
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseService.class);
    private static DatabaseService instance;
    private static Pool pool;

    private DatabaseService() {}

    public static synchronized DatabaseService getInstance()
    {
        if (instance == null)
        {
            instance = new DatabaseService();
            connect(Main.getVertx());
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
                .setPort(Constants.DB_PORT)
                .setHost(Constants.DB_HOST)
                .setDatabase(Constants.DB_NAME)
                .setUser(Constants.DB_USER)
                .setPassword(Constants.DB_PASSWORD);

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