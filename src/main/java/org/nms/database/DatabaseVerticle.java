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
import org.nms.database.queries.PollingQueries;
import org.nms.database.queries.ProvisionQueries;
import org.nms.endPoints.ApiResponse;
import org.nms.service.DatabaseService;
import org.nms.service.PollingService;
import org.nms.utils.Constants;
import org.nms.utils.DbUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatabaseVerticle extends AbstractVerticle
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseVerticle.class);
    private DatabaseService dbService;

    @Override
    public void start(Promise<Void> startPromise)
    {
        dbService = DatabaseService.getInstance();
        LOGGER.info("DatabaseVerticle starting...");

        init()
                .compose(v ->
                {
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

        storeProvisionCache();

        return promise.future();
    }

    private void storeProvisionCache()
    {
        try
        {
            dbService.executeQuery(ProvisionQueries.SELECT_ALL_STATUS_TRUE_PROVISIONS)
                    .onSuccess(rows ->
                    {
                        var data = convertRowsToJson(rows);
                        var provisionList = new JsonArray();

                        var rowsArray = data.getJsonArray(Constants.ROWS, new JsonArray());
                        for (var i = 0; i < rowsArray.size(); i++)
                        {
                            try
                            {
                                var row = rowsArray.getJsonObject(i);
                                var provision = new JsonObject()
                                        .put(Constants.MONITOR_ID, row.getInteger(Constants.MONITOR_ID))
                                        .put(Constants.DISC_IP_ADDRESS, row.getString(Constants.DISC_IP_ADDRESS))
                                        .put(Constants.DISC_PORT_NO, row.getInteger(Constants.DISC_PORT_NO))
                                        .put(Constants.CRED_USERNAME, row.getString(Constants.CRED_USERNAME))
                                        .put(Constants.CRED_PASSWORD, row.getString(Constants.CRED_PASSWORD))
                                        .put(Constants.CRED_PROTOCOL, row.getString(Constants.CRED_PROTOCOL))
                                        .put(Constants.STATUS, row.getBoolean(Constants.PROVISION_STATUS, true))
                                        .put(Constants.DISC_WAIT_TIME,row.getInteger(Constants.DISC_WAIT_TIME))
                                        .put(Constants.LAST_POOL, row.getString(Constants.LAST_POOL));

                                provisionList.add(provision);
                                PollingService.cache.put(row.getLong(Constants.MONITOR_ID), provision);
                            }
                            catch (Exception exception)
                            {
                                LOGGER.error("Error processing row {}: {}", i, exception.getMessage());
                            }
                        }

                        LOGGER.info("Cached {} devices to monitor", PollingService.cache.size());
                    });
        }
        catch (Exception exception)
        {
            LOGGER.error("Error in getDeviceToMonitor: {}", exception.getMessage());
        }
    }

    private void setupEventBusConsumers()
    {

        vertx.eventBus().<JsonObject>localConsumer(Constants.DB_EXECUTE_EVENTBUS, handler ->
        {
            try
            {
                var request = handler.body();

                var query = request.getString(Constants.DB_QUERY);

                var jsonParams = request.getJsonArray(Constants.DB_PARAMS);

                if (jsonParams == null || jsonParams.isEmpty())
                {
                    dbService.executeQuery(query)
                            .onSuccess(rows ->
                            {
                                handler.reply(convertRowsToJson(rows));
                            })
                            .onFailure(error ->
                            {
                                LOGGER.error("Error executing query: {}", error.getMessage());
                                handler.fail(500, error.getMessage());
                            });
                }
                else
                {
                    var params = DbUtil.jsonArrayToTuple(jsonParams);

                    dbService.executePreparedQuery(query, params)
                            .onSuccess(rows ->
                            {
                                handler.reply(convertRowsToJson(rows));
                            })
                            .onFailure(error ->
                            {
                                LOGGER.error("Error executing query: {}", error.getMessage());
                                handler.fail(500, error.getMessage());
                            });
                }
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
            try
            {
                var jsonRow = new JsonObject();
                for (var i = 0; i < row.size(); i++)
                {
                    try
                    {
                        jsonRow.put(row.getColumnName(i), row.getValue(i));
                    }
                    catch (Exception exception)
                    {
                        LOGGER.error("Error processing column {}: {}", row.getColumnName(i), exception.getMessage());
                    }
                }
                rowsArray.add(jsonRow);
            }
            catch (Exception exception)
            {
                LOGGER.error("Error processing row: {}", exception.getMessage());
            }
        }

        result.put(Constants.ROW_COUNT, rows.rowCount());
        result.put(Constants.ROWS, rowsArray);
        return result;
    }
    @Override
    public void stop(Promise<Void> stopPromise)
    {
        LOGGER.info("DatabaseVerticle stopping...");
        // Cleanup resources if needed
        vertx.eventBus().localConsumer(Constants.DB_EXECUTE_EVENTBUS).unregister();
        stopPromise.complete();
    }
}