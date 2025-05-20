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

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class PollingService extends BaseService<JsonObject>
{
    private static final Logger LOGGER = LoggerFactory.getLogger(PollingService.class);

    public static final String POLLING_ID = "monitorId";
    private static final String EVENT_PROVISION_CHANGED = "provision.changed";

    public static final String[] CREATE_PARAM_MAPPING = {
            Constants.MONITOR_ID,
            Constants.POLLING_DATA,
            Constants.POLLING_TIMESTAMP
    };

    public static final String[] UPDATE_PARAM_MAPPING = {
            Constants.POLLING_DATA,
            Constants.POLLING_TIMESTAMP,
            Constants.MONITOR_ID
    };

    private final ConcurrentHashMap<Long, JsonObject> cache = new ConcurrentHashMap<>();

    public PollingService() {
        super();
        setupEventBusConsumer();
    }

    private void setupEventBusConsumer()
    {
        vertx.eventBus().<JsonObject>consumer(EVENT_PROVISION_CHANGED, message ->
        {

            var event =  message.body();

            var action = event.getString("action");
            var provision = event.getJsonObject("provision");

            var monitorId = provision != null ? provision.getLong(Constants.MONITOR_ID) : null;

            if (monitorId == null)
            {
                LOGGER.warn("Provision ID missing in event: {}", event.encodePrettily());
                return;
            }

            switch (action)
            {
                case "create":
                    // Add provision to cache
                    cache.put(monitorId, provision);
                    LOGGER.info("Added provision {} to cache", monitorId);
                    break;

                case "update":
                    // Add or update provision in cache
                    var updateProvision = cache.get(monitorId);

                    if(updateProvision != null)
                    {
                        var status = provision.getBoolean(Constants.PROVISION_STATUS, true);
                        cache.put(monitorId, updateProvision.put("status",status));
                        LOGGER.info("Added/Updated provision {} in cache", monitorId);
                    }
                    break;

                case "delete":
                    // Mark provision as inactive
                    cache.remove(monitorId);
                    break;

                default:
                    LOGGER.warn("Unknown action in event: {}", action);
            }
        });
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
    protected String getSelectByIdQuery()
    {
        return PollingQueries.SELECT_POLLING_PROFILE_BY_ID;
    }

    @Override
    protected String getUpdateQuery()
    {
        return PollingQueries.UPDATE_POLLING_DATA_PROFILE;
    }

    @Override
    protected String getDeleteQuery()
    {
        return PollingQueries.DELETE_POLLING_PROFILE;
    }

    @Override
    protected String getIdField()
    {
        return POLLING_ID;
    }

    @Override
    protected String[] getJsonToParamsCreateMapping()
    {
        return CREATE_PARAM_MAPPING;
    }

    @Override
    protected String[] getJsonToParamsUpdateMapping()
    {
        return UPDATE_PARAM_MAPPING;
    }

    @Override
    protected Function<JsonObject, JsonObject> getResponseMapper()
    {
        return json -> new JsonObject()
                .put("monitorId", json.getInteger(Constants.MONITOR_ID))
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
        LOGGER.info("Fetching devices to monitor");

        // Check if cache has active provisions
        var activeProvisions = new JsonArray();
        cache.forEach((provisionId, provision) ->
        {
            if (provision.getBoolean("status", true))
            {
                activeProvisions.add(provision);
            }
        });

        if (!activeProvisions.isEmpty())
        {
            LOGGER.info("cache hit, {} active devices from cache", activeProvisions.size());
            return Future.succeededFuture(new JsonObject().put("provisions", activeProvisions));
        }

        try
        {
            var dbRequest = new JsonObject()
                    .put(Constants.DB_QUERY, ProvisionQueries.SELECT_ALL_STATUS_TRUE_PROVISIONS);

            return vertx.eventBus().<JsonObject>request(Constants.DB_EXECUTE_WITHOUT_PARAM_EVENTBUS, dbRequest)
                    .compose(reply ->
                    {
                        var rows = reply.body();
                        var provisionList = new JsonArray();

                        LOGGER.info("Received rows: {}", rows.size());
                        var rowsArray = rows.getJsonArray("rows", new JsonArray());
                        for (var i = 0; i < rowsArray.size(); i++)
                        {
                            try
                            {
                                var row = rowsArray.getJsonObject(i);
                                var provision = new JsonObject()
                                        .put("monitor_id", row.getInteger(Constants.MONITOR_ID))
                                        .put("ip", row.getString(Constants.DISC_IP_ADDRESS))
                                        .put("port", row.getInteger(Constants.DISC_PORT_NO))
                                        .put("username", row.getString(Constants.CRED_USERNAME))
                                        .put("password", row.getString(Constants.CRED_PASSWORD))
                                        .put("protocol", row.getString(Constants.CRED_PROTOCOL))
                                        .put("status", row.getBoolean(Constants.PROVISION_STATUS, true));

                                provisionList.add(provision);
                                cache.put(row.getLong(Constants.MONITOR_ID), provision);
                            }
                            catch (Exception exception)
                            {
                                LOGGER.error("Error processing row {}: {}", i, exception.getMessage());
                            }
                        }

                        LOGGER.info("Cached {} devices to monitor", provisionList.size());
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

    public Future<JsonObject> insertPollingData(JsonObject params)
    {
        LOGGER.info("Inserting polling data: {}", params);
        return create(params);
    }
}