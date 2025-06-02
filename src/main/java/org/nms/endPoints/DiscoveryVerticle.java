package org.nms.endPoints;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.nms.poller.ZMQCommunication;
import org.nms.service.DiscoveryService;
import org.nms.utils.Constants;
import org.nms.utils.DeviceReachability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class DiscoveryVerticle extends AbstractVerticle
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DiscoveryVerticle.class);
    private static DiscoveryService discoveryService;
    private final Map<String, ResponseHandler> pendingDiscoveries = new HashMap<>();


    @Override
    public void start()
    {
        discoveryService = new DiscoveryService();
        // Register consumer for discovery requests
        vertx.eventBus().<JsonObject>localConsumer("discovery.run", message ->
        {
            var body = message.body();
            var discoveryId = body.getLong(Constants.DISCOVERY_ID);
            var replyAddress = body.getString(Constants.REPLY_ADDRESS); // Dynamic reply address

            if (replyAddress == null) {
                LOGGER.error("No reply address provided for discoveryId: {}", discoveryId);
                return;
            }

            runDiscovery(body);
        });

        // Initialize ZMQ response consumer
        initializeSharedConsumer();
    }

    private void initializeSharedConsumer() {
        vertx.eventBus().<JsonObject>consumer(ZMQCommunication.EB_DISCOVERY_ZMQ_RESPONSE, msg ->
        {
            var responseBody = msg.body();
            var requestId = responseBody.getString(Constants.REQUEST_ID);
            var handler = pendingDiscoveries.remove(requestId);

            if (handler != null) {
                vertx.cancelTimer(handler.timeoutId);
                handler.promise.complete(responseBody);
            }
        });
    }

//    private void runDiscovery(Long discoveryId, String replyAddress)
//    {
//        try
//        {
//            if (discoveryId == null)
//            {
//                vertx.eventBus().send(replyAddress, ApiResponse.error(400, "discoveryId is required").toJson());
//                return;
//            }
//
//            LOGGER.info("Running discovery for discoveryId: {}", discoveryId);
//
//            var dbRequest = new JsonObject()
//                    .put(Constants.DB_QUERY, DiscoveryQueries.SELECT_DISCOVERY_BY_ID_WITH_CREDENTIALS)
//                    .put(Constants.DB_PARAMS, new JsonArray().add(discoveryId));
//
//            // Use customQueryExecutor for DB query
//            discoveryService.customQueryExecutor(dbRequest).onComplete(dbResult ->
//            {
//                LOGGER.info(
//                        "DB query result for discoveryId {}: {}", discoveryId, dbResult.result());
//                if (dbResult.failed())
//                {
//                    LOGGER.error("Failed to fetch discovery profile: {}", dbResult.cause().getMessage());
//                    vertx.eventBus().send(replyAddress, ApiResponse.error(500, dbResult.cause().getMessage()).toJson());
//                    return;
//                }
//
//                var rows = dbResult.result().getJsonObject(Constants.RESULT);
//                if (rows.getInteger(Constants.ROW_COUNT) == 0)
//                {
//                    LOGGER.error("Discovery profile not found with ID: {}", discoveryId);
//                    vertx.eventBus().send(replyAddress, ApiResponse.error(404, "Discovery profile not found").toJson());
//                    return;
//                }
//
//                var row = rows.getJsonArray(Constants.ROWS).getJsonObject(0);
//                var discoveryDetails = buildDiscoveryDetails(row);

    /// /                        .put(Constants.DISCOVERY_ID, row.getLong(Constants.DISC_ID))
    /// /                        .put(Constants.DISC_NAME, row.getString(Constants.DISC_NAME))
    /// /                        .put(Constants.DISC_IP_ADDRESS, row.getString(Constants.DISC_IP_ADDRESS))
    /// /                        .put(Constants.DISC_PORT_NO, row.getInteger(Constants.DISC_PORT_NO))
    /// /                        .put(Constants.STATUS, row.getBoolean(Constants.STATUS))
    /// /                        .put(Constants.DISC_CREDENTIAL_ID, row.getLong(Constants.DISC_CREDENTIAL_ID))
    /// /                        .put(Constants.CRED_USERNAME, row.getValue(Constants.CRED_USERNAME))
    /// /                        .put(Constants.CRED_PASSWORD, row.getValue(Constants.CRED_PASSWORD))
    /// /                        .put(Constants.CRED_PROTOCOL, row.getValue(Constants.CRED_PROTOCOL));
//
//                var ipAddress = discoveryDetails.getString(Constants.DISC_IP_ADDRESS);
//                var portNo = row.getInteger(Constants.DISC_PORT_NO);
//                var wait_time = row.getInteger(Constants.DISC_WAIT_TIME);
//
//                // Perform blocking operations (ping, port check)
//                vertx.executeBlocking(blockingPromise ->
//                {
//                    // Step 1: Ping Check
//                    var pingSuccess = DeviceReachability.performPingCheck(ipAddress);
//                    if (!pingSuccess)
//                    {
//                        LOGGER.error("Ping check failed for IP: {}", ipAddress);
//                        discoveryDetails.put(Constants.STATUS, false)
//                                .put(Constants.DISC_LAST_DISCOVERY_TIME, Instant.now().toString())
//                                .put(Constants.MESSAGE, "Ping check failed: Device is not reachable");
//                        discoveryService.update(discoveryDetails);
//                        blockingPromise.fail("Ping check failed");
//                        return;
//                    }
//
//                    LOGGER.info("Ping check successful for IP: {}", ipAddress);
//
//                    // Step 2: Port Check
//                    var portSuccess = DeviceReachability.performPortCheck(ipAddress, portNo);
//                    if (!portSuccess)
//                    {
//                        LOGGER.error("Port check failed for IP: {}, Port: {}", ipAddress, portNo);
//                        discoveryDetails.put(Constants.STATUS, false)
//                                .put(Constants.DISC_LAST_DISCOVERY_TIME, Instant.now().toString())
//                                .put(Constants.MESSAGE, "Port check failed: Device is not reachable");
//                        discoveryService.update(discoveryDetails);
//                        blockingPromise.fail("Port check failed for Discovery Id: " + discoveryId);
//                        return;
//                    }
//
//                    LOGGER.info("Port check successful for IP: {}, Port: {}", ipAddress, portNo);
//                    blockingPromise.complete();
//                }, result ->
//                {
//                    if (result.failed())
//                    {
//                        LOGGER.error("Failed to perform checks: {}", result.cause().getMessage());
//                        vertx.eventBus().send(replyAddress, ApiResponse.error(400, result.cause().getMessage()).toJson());
//                        return;
//                    }
//
//                    // Step 3: SSH Check via ZMQ
//                    var requestId = "discovery-" + discoveryId + "-" + System.currentTimeMillis();
//                    var zmqPromise = Promise.<JsonObject>promise();
//
//                    // Set timeout (2 seconds)
//                    long timeoutId = vertx.setTimer(wait_time*1000, id ->
//                    {
//                        var removed = pendingDiscoveries.remove(requestId);
//                        if (removed != null) {
//                            zmqPromise.complete(ApiResponse.error(408, "Timeout waiting for discovery response").toJson());
//                        }
//                    });
//
//                    pendingDiscoveries.put(requestId, new ResponseHandler(zmqPromise, timeoutId));
//
//                    var zmqRequest = new JsonObject()
//                            .put(Constants.REQUEST_ID, requestId)
//                            .put(Constants.COMMAND, "discovery")
//                            .put(Constants.DATA, new JsonArray().add(discoveryDetails));
//
//                    // Send request (no reply needed)
//                    vertx.eventBus().send(ZMQCommunication.EB_ZMQ_SEND, zmqRequest);
//
//                    zmqPromise.future().onComplete(zmqResult ->
//                    {
//                        if (zmqResult.failed())
//                        {
//                            LOGGER.error("ZMQ failed: {}", zmqResult.cause().getMessage());
//                            vertx.eventBus().send(replyAddress, ApiResponse.error(500, zmqResult.cause().getMessage()).toJson());
//                            return;
//                        }
//
//                        var zmqResponse = zmqResult.result();
//                        var discoverySuccess = zmqResponse.getBoolean(Constants.SUCCESS, false);
//                        var details = zmqResponse.getString(Constants.DETAILS, "");
//
//                        var response = new JsonObject()
//                                .put(Constants.DISCOVERY_ID, discoveryId)
//                                .put(Constants.SUCCESS, discoverySuccess)
//                                .put(Constants.DETAILS, details);
//
//                        vertx.eventBus().send(replyAddress, ApiResponse.success(response).toJson());
//
//
//                        discoveryDetails.put(Constants.STATUS, discoverySuccess)
//                                .put(Constants.DISC_LAST_DISCOVERY_TIME, Instant.now().toString())
//                                .put(Constants.MESSAGE, details)
//                                .put(Constants.DISCOVERY_ID, discoveryId);
//
//                        discoveryService.update(discoveryDetails);
//                    });
//                });
//            });
//        }
//        catch (Exception exception)
//        {
//            LOGGER.error("Error in runDiscovery: {}", exception.getMessage());
//            vertx.eventBus().send(replyAddress, ApiResponse.error(500, exception.getMessage()).toJson());
//        }
//    }

    private void runDiscovery(JsonObject body)
    {
        var replyAddress = body.getString(Constants.REPLY_ADDRESS);

        try {
            var discoveryId = body.getLong(Constants.DISCOVERY_ID);
            var ipAddress = body.getString(Constants.DISC_IP_ADDRESS);
            var portNo = body.getInteger(Constants.DISC_PORT_NO);
            var wait_time = body.getInteger(Constants.DISC_WAIT_TIME);

            // Perform blocking operations (ping, port check)
            vertx.executeBlocking(blockingPromise ->
            {
                // Step 1: Ping Check
                var pingSuccess = DeviceReachability.performPingCheck(ipAddress);

                if (!pingSuccess)
                {
                    LOGGER.error("Ping check failed for IP: {}", ipAddress);
                    updateDiscoveryStatus(body, false, "Ping check failed: Device is not reachable");
                    blockingPromise.fail("Ping check failed for Discovery Id: " + discoveryId);
                    return;
                }

                LOGGER.info("Ping check successful for IP: {}", ipAddress);

                // Step 2: Port Check
                var portSuccess = DeviceReachability.performPortCheck(ipAddress, portNo);
                if (!portSuccess)
                {
                    LOGGER.error("Port check failed for IP: {}, Port: {}", ipAddress, portNo);
                    updateDiscoveryStatus(body, false, "Port check failed: Device is not reachable");
                    blockingPromise.fail("Port check failed for Discovery Id: " + discoveryId);
                    return;
                }

                LOGGER.info("Port check successful for IP: {}, Port: {}", ipAddress, portNo);
                blockingPromise.complete();

            }, result ->
            {
                if (result.failed())
                {
                    LOGGER.error("Failed to perform checks: {}", result.cause().getMessage());
                    vertx.eventBus().send(replyAddress, ApiResponse.error(400, result.cause().getMessage()).toJson());
                    return;
                }

                // Step 3: SSH Check via ZMQ
                var requestId = "discovery-" + discoveryId + "-" + System.currentTimeMillis();
                var zmqPromise = Promise.<JsonObject>promise();

                // Set timeout (e.g., 2 seconds)
                long timeoutId = vertx.setTimer(wait_time * 1000, id ->
                {
                    var removed = pendingDiscoveries.remove(requestId);
                    if (removed != null) {
                        zmqPromise.complete(ApiResponse.error(408, "Timeout waiting for discovery response").toJson());
                    }
                });

                pendingDiscoveries.put(requestId, new ResponseHandler(zmqPromise, timeoutId));

                var zmqRequest = new JsonObject()
                        .put(Constants.REQUEST_ID, requestId)
                        .put(Constants.COMMAND, Constants.COMMAND_DISCOVERY)
                        .put(Constants.DATA, new JsonArray().add(body));

                // Send request (no reply needed)
                vertx.eventBus().send(ZMQCommunication.EB_ZMQ_SEND, zmqRequest);

                zmqPromise.future().onComplete(zmqResult ->
                {
                    if (zmqResult.failed())
                    {
                        LOGGER.error("ZMQ failed: {}", zmqResult.cause().getMessage());
                        vertx.eventBus().send(replyAddress, ApiResponse.error(500, zmqResult.cause().getMessage()).toJson());
                        return;
                    }

                    var zmqResponse = zmqResult.result();
                    var discoverySuccess = zmqResponse.getBoolean(Constants.SUCCESS, false);
                    var details = zmqResponse.getString(Constants.DETAILS, "");

                    var response = new JsonObject()
                            .put(Constants.DISCOVERY_ID, discoveryId)
                            .put(Constants.SUCCESS, discoverySuccess)
                            .put(Constants.DETAILS, details);

                    vertx.eventBus().send(replyAddress, ApiResponse.success(response).toJson());

                    updateDiscoveryStatus(body, discoverySuccess, details);
                });
            });

        }
        catch (Exception exception)
        {
            LOGGER.error("Error in runDiscovery: {}", exception.getMessage());
            vertx.eventBus().send(replyAddress, ApiResponse.error(500, exception.getMessage()).toJson());
        }
    }

    public static void updateDiscoveryStatus(JsonObject discoveryDetails, boolean status, String message)
    {
        discoveryDetails.put(Constants.STATUS, status)
                .put(Constants.DISC_LAST_DISCOVERY_TIME, Instant.now().toString())
                .put(Constants.MESSAGE, message);

        discoveryService.update(discoveryDetails);
    }

    private static class ResponseHandler
    {
        final Promise<JsonObject> promise;
        final long timeoutId;

        ResponseHandler(Promise<JsonObject> promise, long timeoutId)
        {
            this.promise = promise;
            this.timeoutId = timeoutId;
        }
    }
}