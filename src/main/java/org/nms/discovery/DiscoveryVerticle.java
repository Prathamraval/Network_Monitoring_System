package org.nms.discovery;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.nms.endPoints.ApiResponse;
import org.nms.poller.ZMQCommunication;
import org.nms.service.DiscoveryService;
import org.nms.utils.Constants;
import org.nms.utils.PromiseRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
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
            var promiseId = body.getString(Constants.PROMISE_ID);

            if (promiseId == null)
            {
                LOGGER.error("No promiseId provided for discoveryId: {}", discoveryId);
                return;
            }

            runDiscovery(body, promiseId);
        });

        // Initialize ZMQ response consumer
        initializeSharedConsumer();
    }

    private void initializeSharedConsumer()
    {
        vertx.eventBus().<JsonObject>consumer(ZMQCommunication.EB_DISCOVERY_ZMQ_RESPONSE, msg ->
        {
            var responseBody = msg.body();
            var requestId = responseBody.getString(Constants.REQUEST_ID);
            var handler = pendingDiscoveries.remove(requestId);

            if (handler != null)
            {
                vertx.cancelTimer(handler.timeoutId);
                var promise = PromiseRegistry.getInstance().getPromise(handler.promiseId);

                LOGGER.info("Retrieved promise for promiseId: {}", handler.promiseId);

                if (promise != null)
                {
                    promise.complete(ApiResponse.success(responseBody.getString(Constants.DETAILS)).toJson());
                }
                else
                {
                    LOGGER.warn("No promise found for promiseId: {}", handler.promiseId);
                }
            }
            else
            {
                LOGGER.warn("No pending discovery found for requestId: {}", requestId);
            }
        });
    }

    private void runDiscovery(JsonObject body, String promiseId)
    {
        var discoveryId = body.getLong(Constants.DISCOVERY_ID);

        try
        {
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
                var promise = PromiseRegistry.getInstance().getPromise(promiseId);

                if (result.failed())
                {
                    LOGGER.error("Failed to perform checks: {}", result.cause().getMessage());
                    if (promise != null)
                    {
                        promise.complete(ApiResponse.error(400, result.cause().getMessage()).toJson());
                    }
                    return;
                }

                // Step 3: SSH Check via ZMQ
                var requestId = "discovery-" + discoveryId + "-" + System.currentTimeMillis();

                // Set timeout
                var timeoutId = vertx.setTimer(wait_time * 1000, id ->
                {
                    var handler = pendingDiscoveries.remove(requestId);

                    if (handler != null)
                    {
                        var timeoutPromise = PromiseRegistry.getInstance().getPromise(handler.promiseId);

                        if (timeoutPromise != null)
                        {
                            updateDiscoveryStatus(body, false, "Timeout waiting for discovery response");
                            timeoutPromise.complete(ApiResponse.error(408, "Timeout waiting for discovery response").toJson());
                        }
                    }
                });

                pendingDiscoveries.put(requestId, new ResponseHandler(promiseId, timeoutId));

                var zmqRequest = new JsonObject()
                        .put(Constants.REQUEST_ID, requestId)
                        .put(Constants.COMMAND, Constants.COMMAND_DISCOVERY)
                        .put(Constants.DATA, new JsonArray().add(body));

                // Send ZMQ request
                LOGGER.info("Sending ZMQ request with requestId: {}", requestId);
                vertx.eventBus().send(ZMQCommunication.EB_ZMQ_SEND, zmqRequest);

                // Handle ZMQ response for status update
                promise.future().onComplete(response ->
                    {
                        LOGGER.info("ZMQ response : {}", response.result());
                        var responseJsonObject = response.result();
                        var discoverySuccess = responseJsonObject.getBoolean(Constants.SUCCESS, false);
                        var details = responseJsonObject.getString(Constants.MESSAGE, "");

                        updateDiscoveryStatus(body, discoverySuccess, details);
                    });
            });
        }
        catch (Exception exception)
        {
            LOGGER.error("Error in runDiscovery: {}", exception.getMessage());
            var promise = PromiseRegistry.getInstance().getPromise(promiseId);

            if (promise != null)
            {
                updateDiscoveryStatus(body, false, "Error: " + exception.getMessage());
                promise.complete(ApiResponse.error(500, exception.getMessage()).toJson());
            }
        }
    }

    public static void updateDiscoveryStatus(JsonObject discoveryDetails, boolean status, String message)
    {
        discoveryDetails.put(Constants.STATUS, status)
                .put(Constants.DISC_LAST_DISCOVERY_TIME, OffsetDateTime.now(Constants.IST_ZONE).toString())
                .put(Constants.MESSAGE, message);

        discoveryService.update(discoveryDetails);
    }

    private static class ResponseHandler
    {
        final String promiseId;
        final long timeoutId;

        ResponseHandler(String promiseId, long timeoutId)
        {
            this.promiseId = promiseId;
            this.timeoutId = timeoutId;
        }
    }
}