package org.nms.service;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.nms.database.queries.PollingQueries;
import org.nms.database.queries.ProvisionQueries;
import org.nms.routerController.ApiResponse;
import org.nms.utils.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.LookupOp;
import java.util.function.Function;

public class PollingService extends BaseService<JsonObject>
{
    private static final Logger LOGGER = LoggerFactory.getLogger(PollingService.class);

    public static final String POLLING_ID = "monitorId";

    public static final String[] CREATE_PARAM_MAPPING = {
            Constants.POLLING_MONITOR_ID,
            Constants.POLLING_DATA,
            Constants.POLLING_TIMESTAMP
    };

    public static final String[] UPDATE_PARAM_MAPPING = {
            Constants.POLLING_DATA,
            Constants.POLLING_TIMESTAMP,
            Constants.POLLING_MONITOR_ID
    };

    public PollingService() {
        super();
    }

    @Override
    protected String getInsertQuery()
    {
        return PollingQueries.INSERT_POLLING_DATA_PROFILE;
    }

    @Override
    protected String getSelectAllQuery()
    {
        return PollingQueries.SELECT_ALL_POLLING_DATA;
    }

    @Override
    protected String getSelectByIdQuery() {
        return PollingQueries.SELECT_POLLING_PROFILE_BY_ID;
    }

    @Override
    protected String getUpdateQuery() {
        return PollingQueries.UPDATE_POLLING_DATA_PROFILE;
    }

    @Override
    protected String getDeleteQuery() {
        return PollingQueries.DELETE_POLLING_PROFILE;
    }

    @Override
    protected String getIdField() {
        return POLLING_ID;
    }

    @Override
    protected String[] getJsonToParamsCreateMapping() {
        return CREATE_PARAM_MAPPING;
    }

    @Override
    protected String[] getJsonToParamsUpdateMapping()
    {
        return UPDATE_PARAM_MAPPING;
    }
    @Override
    protected Function<JsonObject, JsonObject> getResponseMapper() {
        return json -> new JsonObject()
                .put("monitorId", json.getInteger(Constants.POLLING_MONITOR_ID))
                .put("data", json.getJsonObject(Constants.POLLING_DATA))
                .put("timestamp", json.getString(Constants.POLLING_TIMESTAMP));
    }

    @Override
    protected Function<JsonObject, JsonObject> getRowToResponseMapper() {
        return row -> new JsonObject()
                .put("monitorId", row.getInteger("monitor_id"))
                .put("data", row.getJsonObject("data"))
                .put("timestamp", row.getString("timestamp"));
    }

    public Future<JsonObject> getDeviceToMonitor()
    {
        LOGGER.info("Fetching devices to monitor from the database");

        try
        {
            var dbRequest = new JsonObject()
                    .put(Constants.DB_QUERY, ProvisionQueries.SELECT_ALL_STATUS_TRUE_PROVISIONS);

            return vertx.eventBus().<JsonObject>request(Constants.DB_EXECUTE_WITHOUT_PARAM_EVENTBUS, dbRequest)
                    .compose(reply ->
                    {
                        var rows = reply.body();
                        var provisionList = new JsonArray();

                        LOGGER.info(
                                "Received rows: {}",
                                rows.encodePrettily()
                        );
                        var rowsArray = rows.getJsonArray("rows", new JsonArray());
                        for (var i = 0; i < rowsArray.size(); i++)
                        {
                            try
                            {
                                var row = rowsArray.getJsonObject(i);
                                var provision = new JsonObject()
                                        .put("monitor_id", row.getInteger(Constants.POLLING_MONITOR_ID))
                                        .put("ip", row.getString(Constants.DISC_IP_ADDRESS))
                                        .put("port", row.getInteger(Constants.DISC_PORT_NO))
                                        .put("username", row.getString(Constants.CRED_USERNAME))
                                        .put("password", row.getString(Constants.CRED_PASSWORD))
                                        .put("protocol", row.getString(Constants.CRED_PROTOCOL));

                                provisionList.add(provision);
                            }
                            catch (Exception exception)
                            {
                                LOGGER.error("Error processing row {}: {}", i, exception.getMessage());
                            }
                        }

                        return Future.succeededFuture(new JsonObject().put("provisions", provisionList));
                    })
                    .recover(error ->
                    {
                        LOGGER.error("Failed to fetch devices: {}", error.getMessage());

                        return Future.succeededFuture(ApiResponse.error(500, "Failed to fetch devices: " + error.getMessage()).toJson());
                    });
        }
        catch (Exception exception)
        {
            LOGGER.error("Error in getDeviceToMonitor: {}", exception.getMessage());
            return Future.succeededFuture(ApiResponse.error(500, "Error fetching devices: " + exception.getMessage()).toJson());
        }
    }

    public Future<JsonObject> insertPollingData(JsonObject params) {
        LOGGER.info("Inserting polling data: {}", params);
        return create(params);
    }
}