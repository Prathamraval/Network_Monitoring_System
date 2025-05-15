package org.nms.database;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import org.nms.database.queries.CredentialQueries;
import org.nms.database.queries.DiscoveryQueries;
//import org.nms.database.queries.PollingQueries;
//import org.nms.database.queries.ProvisionQueries;
import org.nms.database.queries.PollingQueries;
import org.nms.database.queries.ProvisionQueries;
import org.nms.service.DatabaseService;
import org.nms.utils.Constants;
import org.nms.utils.DbUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatabaseVerticle extends AbstractVerticle {
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseVerticle.class);
    private DatabaseService dbService;

    @Override
    public void start(Promise<Void> startPromise) {
        dbService = DatabaseService.getInstance();
        LOGGER.info("DatabaseVerticle starting...");

        init()
                .compose(v -> {
                    setupEventBusConsumers();
                    return Future.succeededFuture();
                })
                .onSuccess(v -> startPromise.complete())
                .onFailure(startPromise::fail);
    }

    private Future<Void> init()
    {
        var promise = Promise.<Void>promise();
        var sql = CredentialQueries.CREATE_CREDENTIAL_PROFILES_TABLE +
                DiscoveryQueries.CREATE_DISCOVERY_PROFILES_TABLE +
                ProvisionQueries.CREATE_PROVISION_TABLE +
                PollingQueries.CREATE_POLLING_DATA_TABLE;

        dbService.executeQuery(sql)
                .onSuccess(rows -> promise.complete())
                .onFailure(promise::fail);

        return promise.future();
    }

    private void setupEventBusConsumers()
    {
        vertx.eventBus().<JsonObject>localConsumer(Constants.DB_EXECUTE_WITHOUT_PARAM_EVENTBUS, handler ->
        {
            try
            {
                String query = handler.body().getString(Constants.DB_QUERY);
                dbService.executeQuery(query)
                        .onSuccess(rows -> handler.reply(convertRowsToJson(rows)))
                        .onFailure(error ->
                        {
                            LOGGER.error("Error executing query: {}", error.getMessage());
                            handler.fail(500, error.getMessage());
                        });
            }
            catch (Exception exception)
            {
                LOGGER.error("Error processing query request: {}", exception.getMessage());
                handler.fail(400, "Invalid request format: " + exception.getMessage());
            }
        });

        vertx.eventBus().<JsonObject>localConsumer(Constants.DB_EXECUTE_PARAM_EVENTBUS, handler ->
        {
            try
            {
                var request = handler.body();
                var query = request.getString(Constants.DB_QUERY);
                var jsonParams = request.getJsonArray(Constants.DB_PARAMS);
                var params = DbUtil.jsonArrayToTuple(jsonParams);

                dbService.executePreparedQuery(query, params)
                        .onSuccess(rows -> handler.reply(convertRowsToJson(rows)))
                        .onFailure(error ->
                        {
                            LOGGER.error("Error executing query: {}", error.getMessage());
                            handler.fail(500, error.getMessage());
                        });
            }
            catch (Exception exception)
            {
                LOGGER.error("Error processing query request: {}", exception.getMessage());
                handler.fail(400, "Invalid request format: " + exception.getMessage());
            }
        });
    }

    private JsonObject convertRowsToJson(RowSet<Row> rows)
    {
        var result = new JsonObject();
        var rowsArray = new JsonArray();

        for (var row : rows)
        {
            var jsonRow = new JsonObject();
            for (var i = 0; i < row.size(); i++)
            {
                jsonRow.put(row.getColumnName(i), row.getValue(i));
            }
            rowsArray.add(jsonRow);
        }

        result.put("rowCount", rows.rowCount());
        result.put("rows", rowsArray);
        return result;
    }
}