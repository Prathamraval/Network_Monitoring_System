package org.nms.service;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.Promise;
import org.nms.database.queries.DiscoveryQueries;
import org.nms.endPoints.ApiResponse;
import org.nms.utils.Constants;
import org.nms.utils.PromiseRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.function.Function;

public class DiscoveryService extends BaseService<JsonObject>
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DiscoveryService.class);

    public static final String[] CREATE_PARAM_MAPPING = {
            Constants.DISC_NAME,
            Constants.DISC_CREDENTIAL_ID,
            Constants.DISC_IP_ADDRESS,
            Constants.DISC_PORT_NO,
            Constants.DISC_WAIT_TIME
    };

    public static final String[] UPDATE_PARAM_MAPPING = {
            Constants.DISC_NAME,
            Constants.DISC_CREDENTIAL_ID,
            Constants.DISC_IP_ADDRESS,
            Constants.DISC_PORT_NO,
            Constants.STATUS,
            Constants.DISC_LAST_DISCOVERY_TIME,
            Constants.MESSAGE,
            Constants.DISCOVERY_ID
    };

    @Override
    protected String getInsertQuery()
    {
        return DiscoveryQueries.INSERT_DISCOVERY_PROFILE;
    }

    @Override
    protected String getSelectAllQuery()
    {
        return DiscoveryQueries.SELECT_DISCOVERY_WITH_CREDENTIALS;
    }

    @Override
    protected String getSelectByIdQuery()
    {
        return DiscoveryQueries.SELECT_DISCOVERY_BY_ID_WITH_CREDENTIALS;
    }

    @Override
    protected String getUpdateQuery()
    {
        return DiscoveryQueries.UPDATE_DISCOVERY_PROFILE;
    }

    @Override
    protected String getDeleteQuery()
    {
        return DiscoveryQueries.DELETE_DISCOVERY_PROFILE;
    }

    @Override
    protected String getIdField()
    {
        return Constants.DISCOVERY_ID;
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
                .put("discovery_name", json.getString(Constants.DISC_NAME));
    }

    @Override
    protected Function<JsonObject, JsonObject> getRowToResponseMapper()
    {
        return row ->
        {
            var credentials = new JsonObject()
                    .put(Constants.CRED_ID, row.getLong(Constants.DISC_CREDENTIAL_ID))
                    .put(Constants.CRED_USERNAME, row.getString(Constants.CRED_USERNAME))
                    .put(Constants.CRED_PROTOCOL, row.getString(Constants.CRED_PROTOCOL))
                    .put(Constants.CRED_PROFILENAME, row.getString(Constants.CRED_PROFILENAME));

            return new JsonObject()
                    .put("id", row.getLong(Constants.DISC_ID))
                    .put("discovery.name", row.getString(Constants.DISC_NAME))
                    .put("ipAddress", row.getString(Constants.DISC_IP_ADDRESS))
                    .put("portNo", row.getInteger(Constants.DISC_PORT_NO))
                    .put("status", row.getBoolean(Constants.STATUS))
                    .put("message", row.getString(Constants.MESSAGE, ""))
                    .put("wait_time", row.getInteger(Constants.DISC_WAIT_TIME))
                    .put(Constants.DISC_LAST_DISCOVERY_TIME, row.getValue(Constants.DISC_LAST_DISCOVERY_TIME))
                    .put("credentials", credentials);
        };
    }

    public Future<JsonObject> getDiscoveriesByStatus(boolean status)
    {
        LOGGER.info("Fetching discovery profiles by status: {}", status);

        try
        {
            var query = DiscoveryQueries.SELECT_DISCOVERY_BY_STATUS_WITH_CREDENTIALS;
            var params = new JsonArray().add(status);

            var dbRequest = new JsonObject()
                    .put(Constants.DB_QUERY, query)
                    .put(Constants.DB_PARAMS, params);
            var promise = Promise.<JsonObject>promise();

            vertx.eventBus().<JsonObject>request(Constants.DB_EXECUTE_EVENTBUS, dbRequest, reply ->
            {
                try
                {
                    if (reply.succeeded())
                    {
                        var rows = reply.result().body();
                        if (rows.getInteger(Constants.ROW_COUNT, 0) == 0)
                        {
                            LOGGER.error("No discovery profiles found with status: {}", status);
                            promise.complete(ApiResponse.error(404, "No discovery profiles found with status: " + status).toJson());
                            return;
                        }

                        var discoveries = new JsonArray();
                        var rowsArray = rows.getJsonArray(Constants.ROWS, new JsonArray());

                        for (var i = 0; i < rowsArray.size(); i++)
                        {
                            var row = rowsArray.getJsonObject(i);
                            discoveries.add(getRowToResponseMapper().apply(row));
                        }

                        promise.complete(ApiResponse.success(new JsonObject().put("discoveries", discoveries)).toJson());
                    }
                    else
                    {
                        LOGGER.error("Error fetching by status: {}", reply.cause().getMessage());

                        promise.complete(ApiResponse.error(500, reply.cause().getMessage()).toJson());
                    }
                }
                catch (Exception exception)
                {
                    LOGGER.error("Error processing DB response: {}", exception.getMessage());

                    promise.complete(ApiResponse.error(500, "Error processing DB response").toJson());
                }
            });

            return promise.future();
        }
        catch (Exception exception)
        {
            LOGGER.error("Error in getDiscoveriesByStatus: {}", exception.getMessage());
            return Future.succeededFuture(ApiResponse.error(500, exception.getMessage()).toJson());
        }
    }


    public Future<JsonObject> processRunDiscovery(Long discoveryId)
    {
        var promise = Promise.<JsonObject>promise();
        try
        {
            if (discoveryId == null)
            {
                return Future.failedFuture("discoveryId is required");
            }

            LOGGER.info("Sending discovery request for ID: {}", discoveryId);

            // Create a unique reply address
            var replyAddress = "discovery.reply." + discoveryId ;

            // Set up temporary consumer for response
            var consumer = vertx.eventBus().<JsonObject>localConsumer(replyAddress, message ->
            {
                LOGGER.info("Received discovery response {}", message.body());
                promise.complete(message.body());
                // Unregister the consumer after handling the response
            });

            // Send discoveryId to DiscoveryVerticle with reply address
            var request = new JsonObject()
                    .put(Constants.DISCOVERY_ID, discoveryId)
                    .put(Constants.REPLY_ADDRESS, replyAddress);

            vertx.eventBus().send("discovery.run", request);

            return promise.future().onComplete(ar ->
            {
                consumer.unregister();
                LOGGER.debug("Unregistered consumer for address: {}", replyAddress);
            });

        }
        catch (Exception exception)
        {
            LOGGER.error("Error in runDiscovery: {}", exception.getMessage());
            return Future.succeededFuture(ApiResponse.error(500, exception.getMessage()).toJson());
        }
    }

    public Future<JsonObject> processRunDiscovery(JsonObject requestBody)
    {
        var promise = Promise.<JsonObject>promise();
        try
        {
            requestBody = new JsonObject(requestBody.encode()); // Defensive copy
            var discoveryId = requestBody.getLong(Constants.DISCOVERY_ID);
            var promiseId = "promise-" + discoveryId + "-" + UUID.randomUUID().toString();

            // Register promise in PromiseRegistry
            PromiseRegistry.getInstance().registerPromise(promiseId, promise);

            // Add promiseId to request body
            requestBody.put(Constants.PROMISE_ID, promiseId);

            // Send to DiscoveryVerticle
            LOGGER.info("Sending discovery request with promiseId: {}", promiseId);
            vertx.eventBus().send("discovery.run", requestBody);

            // Clean up registry when promise completes
            return promise.future().onComplete(ar ->
            {
                PromiseRegistry.getInstance().removePromise(promiseId);
                LOGGER.debug("Removed promise for promiseId: {}", promiseId);
            });
        }
        catch (Exception exception)
        {
            LOGGER.error("Error in processRunDiscovery: {}", exception.getMessage());
            return Future.succeededFuture(ApiResponse.error(500, exception.getMessage()).toJson());
        }
    }
}