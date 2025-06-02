package org.nms.service;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.nms.database.queries.PollingQueries;
import org.nms.utils.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.function.Function;

public class PollingService extends BaseService<JsonObject>
{
    private static final Logger LOGGER = LoggerFactory.getLogger(PollingService.class);

    public static final String MONITORID = "monitorId";

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

    public static final HashMap<Long, JsonObject> cache = new HashMap<>();

    public PollingService()
    {
        super();
        setupEventBusConsumer();
    }

    private void setupEventBusConsumer()
    {
        vertx.eventBus().<JsonObject>consumer(Constants.EVENT_PROVISION_CHANGED, message ->
        {
            var event = message.body();
            var action = event.getString(Constants.ACTION);
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
                    // Add provision to cache with last_pool
                    cache.put(monitorId, provision);
                    LOGGER.info("Added provision {} to cache with last_poll: {}", provision, provision.getString(Constants.LAST_POLL));
                    break;

                case "update":
                    // Update provision in cache, preserving last_poll unless provided
                    var existingProvision = cache.get(monitorId);
                    if (existingProvision != null)
                    {
                        var updatedProvision = existingProvision.copy();
                        updatedProvision.put(Constants.STATUS, provision.getBoolean(Constants.PROVISION_STATUS, true));
                        if (provision.containsKey(Constants.LAST_POLL))
                        {
                            updatedProvision.put(Constants.LAST_POLL, provision.getString(Constants.LAST_POLL));
                        }
                        cache.put(monitorId, updatedProvision);
                        LOGGER.info("Updated provision {} in cache, last_poll: {}", monitorId, updatedProvision.getString(Constants.LAST_POLL));
                    }
                    break;

                case "delete":
                    // Remove provision from cache
                    cache.remove(monitorId);
                    LOGGER.info("Removed provision {} from cache", monitorId);
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
        return MONITORID;
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
                .put(MONITORID, json.getInteger(Constants.MONITOR_ID))
                .put(Constants.DATA, json.getJsonObject(Constants.POLLING_DATA))
                .put(Constants.POLLING_TIMESTAMP, json.getString(Constants.POLLING_TIMESTAMP));
    }

    @Override
    protected Function<JsonObject, JsonObject> getRowToResponseMapper()
    {
        return row -> new JsonObject()
                .put(MONITORID, row.getInteger(Constants.MONITOR_ID))
                .put(Constants.DATA, row.getJsonObject(Constants.POLLING_DATA))
                .put(Constants.POLLING_TIMESTAMP, row.getString(Constants.POLLING_TIMESTAMP));
    }


    public Future<JsonObject> getDeviceToMonitor(HashMap<Long, String> pendingDevices)
    {
        LOGGER.info("Fetching devices to monitor");

            var activeProvisions = new JsonArray();
            LOGGER.info("Cache: {}, Active provisions: {}", cache, activeProvisions);

            cache.forEach((provisionId, provision) ->
            {
                try
                {
                    if (provision.getBoolean(Constants.STATUS, true) && !pendingDevices.containsKey(provisionId))
                    {
                        var lastPoolStr = provision.getString(Constants.LAST_POLL);

                        if (lastPoolStr != null)
                        {
                            try
                            {
                                OffsetDateTime lastPoolTime;
                                try
                                {
                                    lastPoolTime = OffsetDateTime.parse(lastPoolStr);
                                }
                                catch (DateTimeParseException exception)
                                {
                                    lastPoolTime = LocalDateTime.parse(lastPoolStr).atZone(ZoneId.of("Asia/Kolkata")).toOffsetDateTime();
                                }

                                var nextPollTime = lastPoolTime.plusMinutes(5);
                                var currentTime = OffsetDateTime.now();

                                if (nextPollTime.isBefore(currentTime))
                                {
                                    activeProvisions.add(provision);
                                }
                            }
                            catch (Exception exception)
                            {
                                LOGGER.error("Failed to parse last_poll time for provision {}: {}", provisionId, exception.getMessage());
                            }
                        }
                        else
                        {
                            activeProvisions.add(provision);
                        }
                    }
                }
                catch (Exception e)
                {
                    LOGGER.error("Error processing provision ID {}: {}", provisionId, e.getMessage());
                }


            });

            return Future.succeededFuture(new JsonObject().put(Constants.PROVISIONS, activeProvisions));

    }
    public void updateDeviceTimestamp(Long monitorId, String timestamp)
    {
        var provision = cache.get(monitorId);

        if (provision != null)
        {
            cache.put(monitorId, provision.put(Constants.LAST_POLL, timestamp));

            LOGGER.info("Updated last_poll for monitor ID {} to {}", monitorId, timestamp);
        }
    }

    public Future<JsonObject> insertPollingData(JsonObject params)
    {
        LOGGER.info("Inserting polling data: {}", params);

        return create(params);
    }
}