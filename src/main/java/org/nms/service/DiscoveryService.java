package org.nms.service;


import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.Promise;
import org.nms.database.queries.DiscoveryQueries;
import org.nms.polling.ZMQCommunicationVerticle;
import org.nms.routerController.ApiResponse;
import org.nms.utils.Constants;
import org.nms.utils.DeviceReachability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.function.Function;

public class DiscoveryService extends BaseService<JsonObject>
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DiscoveryService.class);

    public static final String[] CREATE_PARAM_MAPPING = {
            Constants.DISC_NAME,
            Constants.DISC_CREDENTIAL_ID,
            Constants.DISC_IP_ADDRESS,
            Constants.DISC_PORT_NO
    };

    public static final String[] UPDATE_PARAM_MAPPING = {
            Constants.DISC_NAME,
            Constants.DISC_CREDENTIAL_ID,
            Constants.DISC_IP_ADDRESS,
            Constants.DISC_PORT_NO,
            Constants.DISC_STATUS,
            Constants.DISC_LAST_DISCOVERY_TIME,
            Constants.DISC_MESSAGE,
            Constants.DISC_ID
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
        return "discovery_id";
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
                    .put("status", row.getBoolean(Constants.DISC_STATUS))
                    .put("lastDiscoveryTime", row.getValue(Constants.DISC_LAST_DISCOVERY_TIME))
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

            vertx.eventBus().<JsonObject>request(Constants.DB_EXECUTE_PARAM_EVENTBUS, dbRequest, reply ->
            {
                try
                {
                    if (reply.succeeded())
                    {
                        var rows = reply.result().body();
                        if (rows.getInteger("rowCount", 0) == 0)
                        {
                            LOGGER.error("No discovery profiles found with status: {}", status);
                            promise.complete(ApiResponse.error(404, "No discovery profiles found with status: " + status).toJson());
                            return;
                        }

                        var discoveries = new JsonArray();
                        var rowsArray = rows.getJsonArray("rows", new JsonArray());

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

    public Future<JsonObject> runDiscovery(Long discoveryId)
    {
        try
        {
            if (discoveryId == null)
            {
                return Future.succeededFuture(ApiResponse.error(400, "discoveryId is required").toJson());
            }

            LOGGER.info("Running discovery for discoveryId: {}", discoveryId);

            var query = DiscoveryQueries.SELECT_DISCOVERY_BY_ID_WITH_CREDENTIALS;

            var params = new JsonArray().add(discoveryId);

            // Create the request to send to DBVerticle
            var dbRequest = new JsonObject()
                    .put(Constants.DB_QUERY, query)
                    .put(Constants.DB_PARAMS, params);

            // Return a Future that will be completed when the DB operation is done
            var promise = Promise.<JsonObject>promise();

            vertx.eventBus().<JsonObject>request(Constants.DB_EXECUTE_PARAM_EVENTBUS, dbRequest, reply ->
            {
                if (reply.succeeded())
                {
                    var rows = reply.result().body();

                    if (rows.getInteger("rowCount", 0) == 0)
                    {
                        LOGGER.error("Discovery profile not found with ID: {}", discoveryId);

                        promise.complete(ApiResponse.error(404, "Discovery profile not found").toJson());

                        return;
                    }

                    var row = rows.getJsonArray("rows").getJsonObject(0);
                    var discoveryDetails = new JsonObject()
                            .put("discovery_id", row.getLong(Constants.DISC_ID))
                            .put("discovery_name", row.getString(Constants.DISC_NAME))
                            .put("ip_address", row.getString(Constants.DISC_IP_ADDRESS))
                            .put("port_no", row.getInteger(Constants.DISC_PORT_NO))
                            .put("status", row.getBoolean(Constants.DISC_STATUS))
                            .put("credential_id", row.getLong(Constants.DISC_CREDENTIAL_ID));

                    var ipAddress = discoveryDetails.getString(Constants.DISC_IP_ADDRESS);
                    var portNo = row.getInteger(Constants.DISC_PORT_NO);
                    var username = row.getString(Constants.CRED_USERNAME);
                    var password = row.getString(Constants.CRED_PASSWORD);
                    var protocol = row.getString(Constants.CRED_PROTOCOL);

                    // Perform all blocking operations (ping, port check) in a single executeBlocking
                    vertx.executeBlocking(blockingPromise ->
                    {
                        try
                        {
                            // Step 1: Ping Check
                            DeviceReachability.performPingCheck(ipAddress);

                            LOGGER.info(
                                    "Ping check successful for IP: {}, Port: {}, Protocol: {}",
                                    ipAddress, portNo, protocol
                            );

                            // Step 2: Port Check
                            DeviceReachability.performPortCheck(ipAddress, portNo);
                            LOGGER.info(
                                    "Port check successful for IP: {}, Port: {}, Protocol: {}",
                                    ipAddress, portNo, protocol
                            );

                            blockingPromise.complete();
                        }
                        catch (Exception exception)
                        {
                            blockingPromise.fail(exception);
                        }
                    }, result ->
                    {
                        if (result.failed())
                        {
                            LOGGER.error("Failed to perform checks: {}", result.cause().getMessage());
                            discoveryDetails.put("status", false)
                                    .put("lastDiscoveryTime", Instant.now().toString())
                                    .put("message", "Failed to perform checks: " + result.cause().getMessage());

                            update(discoveryDetails);

                            promise.complete(ApiResponse.error(400, result.cause().getMessage()).toJson());
                            return;
                        }

                        // Step 3: SSH Check via ZMQ instead of Go Plugin directly
                        // Create a unique request ID for this discovery
                        var requestId = "discovery-" + discoveryId + "-" + System.currentTimeMillis();

                        // Create the ZMQ request payload
                        // The Go plugin expects a different structure - "data" field should have the device info
                        var deviceInput = new JsonObject()
                                .put("ip", ipAddress)
                                .put("port", portNo)
                                .put("username", username)
                                .put("password", password)
                                .put("protocol", protocol.toLowerCase())
                                .put("discovery_id", discoveryId.intValue());

                        var zmqRequest = new JsonObject()
                                .put("request_id", requestId)
                                .put("command", "discovery")
                                .put("data", deviceInput);

                        // Create and register the consumer
                        var consumer = vertx.eventBus().<JsonObject>consumer(ZMQCommunicationVerticle.EB_ZMQ_RESPONSE);

                        // Set a timeout for the response
                        final long timeoutId = vertx.setTimer(30000, id ->
                        {
                            consumer.unregister();
                            promise.complete(ApiResponse.error(408, "Timeout waiting for discovery response").toJson());
                        });

                        // Set up the handler
                        consumer.handler(msg ->
                        {
                            var responseBody = msg.body();
                            LOGGER.info("Received ZMQ response: {}", responseBody.encodePrettily());
                            if (requestId.equals(responseBody.getString("request_id")))
                            {
                                // Unregister the consumer since we got our response
                                consumer.unregister();

                                // Cancel the timeout timer
                                vertx.cancelTimer(timeoutId);

                                var discoverySuccess = responseBody.getBoolean("success", false);
                                String details = responseBody.getString("details", "");

                                discoveryDetails.put("status", discoverySuccess)
                                        .put("lastdiscoverytime", Instant.now().toString())
                                        .put("message", details);

                                LOGGER.info(
                                        "Discovery result for ID {}:",
                                        discoveryDetails
                                );
                                // Create the request to send to DBVerticle
                                update(discoveryDetails)
                                        .onSuccess(updateResponse ->
                                        {
                                            var response = new JsonObject()
                                                    .put("discoveryId", discoveryId)
                                                    .put("success", discoverySuccess)
                                                    .put("details", details);

                                            promise.complete(ApiResponse.success(response).toJson());
                                        })
                                        .onFailure(error ->
                                        {
                                            LOGGER.error("Failed to update discovery profile: {}", error.getMessage());
                                            promise.complete(ApiResponse.error(500, "Failed to update discovery profile").toJson());
                                        });
                            }
                        });

                        // Send the request to the ZMQ verticle
                        vertx.eventBus().<JsonObject>request(ZMQCommunicationVerticle.EB_ZMQ_SEND, zmqRequest, zmqSendReply ->
                        {
                            if (zmqSendReply.failed())
                            {
                                consumer.unregister();
                                vertx.cancelTimer(timeoutId);
                                promise.complete(ApiResponse.error(500, "Failed to send discovery request: " +
                                        zmqSendReply.cause().getMessage()).toJson());
                            }
                            // For successful send, we wait for the response via the consumer
                        });
                    });
                }
                else
                {
                    LOGGER.error("Failed to fetch discovery profile: {}", reply.cause().getMessage());
                    promise.complete(ApiResponse.error(500, reply.cause().getMessage()).toJson());
                }
            });

            return promise.future();
        }
        catch (Exception exception)
        {
            LOGGER.error("Error in runDiscovery: {}", exception.getMessage());
            return Future.succeededFuture(ApiResponse.error(500, exception.getMessage()).toJson());
        }
    }


}