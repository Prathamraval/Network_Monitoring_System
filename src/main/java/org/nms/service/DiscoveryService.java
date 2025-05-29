package org.nms.service;


import io.vertx.core.Future;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.Promise;
import org.nms.database.queries.DiscoveryQueries;
import org.nms.poller.ZMQCommunication;
import org.nms.endPoints.ApiResponse;
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

public Future<JsonObject> runDiscovery(Long discoveryId)
{
    try
    {
        if (discoveryId == null)
        {
            return Future.failedFuture("discoveryId is required");
        }

        LOGGER.info("Running discovery for discoveryId: {}", discoveryId);

        // Use Config for event bus address

        // Create the request to send to DBVerticle
        var dbRequest = new JsonObject()
                .put(Constants.DB_QUERY, DiscoveryQueries.SELECT_DISCOVERY_BY_ID_WITH_CREDENTIALS)
                .put(Constants.DB_PARAMS, new JsonArray().add(discoveryId));

        // Return a Future that will be completed when the DB operation is done
        var promise = Promise.<JsonObject>promise();

        vertx.eventBus().<JsonObject>request(Constants.DB_EXECUTE_EVENTBUS, dbRequest, reply ->
        {
            if (reply.succeeded())
            {
                var rows = reply.result().body();

                if (rows.getInteger(Constants.ROW_COUNT, 0) == 0)
                {
                    LOGGER.error("Discovery profile not found with ID: {}", discoveryId);
                    promise.complete(ApiResponse.error(404, "Discovery profile not found").toJson());
                    return;
                }

                    var row = rows.getJsonArray(Constants.ROWS).getJsonObject(0);
                    var discoveryDetails = new JsonObject()
                            .put(Constants.DISCOVERY_ID, row.getLong(Constants.DISC_ID))
                            .put(Constants.DISC_NAME, row.getString(Constants.DISC_NAME))
                            .put(Constants.DISC_IP_ADDRESS, row.getString(Constants.DISC_IP_ADDRESS))
                            .put(Constants.DISC_PORT_NO, row.getInteger(Constants.DISC_PORT_NO))
                            .put(Constants.STATUS, row.getBoolean(Constants.STATUS))
                            .put(Constants.DISC_CREDENTIAL_ID, row.getLong(Constants.DISC_CREDENTIAL_ID));

                    var ipAddress = discoveryDetails.getString(Constants.DISC_IP_ADDRESS);
                    var portNo = row.getInteger(Constants.DISC_PORT_NO);
                    var username = row.getString(Constants.CRED_USERNAME);
                    var password = row.getString(Constants.CRED_PASSWORD);
                    var protocol = row.getString(Constants.CRED_PROTOCOL);

                // Perform all blocking operations (ping, port check) in a single executeBlocking
                vertx.executeBlocking(blockingPromise ->
                {
                    LOGGER.info("inside executeblocking");
                    // Step 1: Ping Check
                    var pingSuccess = DeviceReachability.performPingCheck(ipAddress);

                    if (!pingSuccess)
                    {
                        LOGGER.error("Ping check failed for IP: {}", ipAddress);

                        discoveryDetails.put(Constants.STATUS, false)
                                .put(Constants.DISC_LAST_DISCOVERY_TIME, Instant.now().toString())
                                .put(Constants.MESSAGE, "Ping check failed: Device is not reachable");

                        update(discoveryDetails);

                        blockingPromise.fail("Ping check failed");

                        return;
                    }

                    LOGGER.info("Ping check successful for IP: {}, Port: {}, Protocol: {}", ipAddress, portNo, protocol);

                    // Step 2: Port Check
                    boolean portSuccess = DeviceReachability.performPortCheck(ipAddress, portNo);

                    if (!portSuccess)
                    {
                        LOGGER.error("Port check failed for IP: {}, Port: {}", ipAddress, portNo);
                        discoveryDetails.put(Constants.STATUS, false)
                                .put(Constants.DISC_LAST_DISCOVERY_TIME, Instant.now().toString())
                                .put(Constants.MESSAGE, "Port check failed: Device is not reachable");

                        update(discoveryDetails);

                        blockingPromise.fail("Port check failed");

                        return;
                    }

                    LOGGER.info("Port check successful for IP: {}, Port: {}, Protocol: {}", ipAddress, portNo, protocol);

                    blockingPromise.complete();
                }, result ->
                {
                    if (result.failed())
                    {
                        LOGGER.error("Failed to perform checks: {}", result.cause().getMessage());
                        promise.complete(ApiResponse.error(400, result.cause().getMessage()).toJson());
                        return;
                    }

                    // Step 3: SSH Check via ZMQ
                    var requestId = "discovery-" + discoveryId + "-" + System.currentTimeMillis();
                    var deviceInputJson = new JsonObject()
                            .put(Constants.DISC_IP_ADDRESS, ipAddress)
                            .put(Constants.DISC_PORT_NO, portNo)
                            .put(Constants.CRED_USERNAME, username)
                            .put(Constants.CRED_PASSWORD, password)
                            .put(Constants.CRED_PROTOCOL, protocol.toLowerCase())
                            .put(Constants.DISCOVERY_ID, discoveryId.intValue());

                    var zmqRequest = new JsonObject()
                            .put(Constants.REQUEST_ID, requestId)
                            .put(Constants.COMMAND, "discovery")
                            .put(Constants.DATA, new JsonArray().add(deviceInputJson));

                    var consumer = vertx.eventBus().<JsonObject>consumer(ZMQCommunication.EB_ZMQ_RESPONSE);

                    final long timeoutId = vertx.setTimer(30000, id ->
                    {
                        consumer.unregister();
                        promise.complete(ApiResponse.error(408, "Timeout waiting for discovery response").toJson());
                    });

                    consumer.handler(msg ->
                    {
                        var responseBody = msg.body();
                        LOGGER.info("Received ZMQ response: {}", responseBody.encodePrettily());

                        if (requestId.equals(responseBody.getString(Constants.REQUEST_ID)))
                        {
                            consumer.unregister();
                            vertx.cancelTimer(timeoutId);

                            var discoverySuccess = responseBody.getBoolean(Constants.SUCCESS, false);
                            var details = responseBody.getString(Constants.DETAILS, "");

                            discoveryDetails.put(Constants.STATUS, discoverySuccess)
                                    .put(Constants.DISC_LAST_DISCOVERY_TIME, Instant.now().toString())
                                    .put(Constants.MESSAGE, details)
                                    .put(Constants.DISCOVERY_ID, discoveryId);

                            LOGGER.info("Discovery result for ID {}: {}", discoveryId, discoveryDetails);

                            update(discoveryDetails)
                                    .onSuccess(updateResponse ->
                                    {
                                        var response = new JsonObject()
                                                .put(Constants.DISCOVERY_ID, discoveryId)
                                                .put(Constants.SUCCESS, discoverySuccess)
                                                .put(Constants.DETAILS, details);
                                        promise.complete(ApiResponse.success(response).toJson());
                                    })
                                    .onFailure(error -> {
                                        LOGGER.error("Failed to update discovery profile: {}", error.getMessage());
                                        promise.complete(ApiResponse.error(500, "Failed to update discovery profile").toJson());
                                    });
                        }
                    });

                    vertx.eventBus().<JsonObject>request(ZMQCommunication.EB_ZMQ_SEND, zmqRequest, new DeliveryOptions().setSendTimeout(3000),zmqSendReply ->
                    {
                        try
                        {
                            if (zmqSendReply.failed())
                            {
                                consumer.unregister();
                                vertx.cancelTimer(timeoutId);
                                promise.complete(ApiResponse.error(500, "Failed to send discovery request: " +
                                        zmqSendReply.cause().getMessage()).toJson());
                            }
                        }
                        catch (Exception exception)
                        {
                            LOGGER.error("Error in ZMQ send reply: {}", exception.getMessage());
                            consumer.unregister();
                            vertx.cancelTimer(timeoutId);
                            promise.complete(ApiResponse.error(500, "Error in ZMQ send reply: " + exception.getMessage()).toJson());
                        }
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
    } catch (Exception exception) {
        LOGGER.error("Error in runDiscovery: {}", exception.getMessage());
        return Future.succeededFuture(ApiResponse.error(500, exception.getMessage()).toJson());
    }
}
}