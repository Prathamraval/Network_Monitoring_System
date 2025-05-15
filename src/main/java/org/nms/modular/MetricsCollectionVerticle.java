package org.nms.modular;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.nms.service.DatabaseService;
import org.nms.service.PollingService;
import org.nms.utils.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Verticle responsible for scheduling and managing metrics collection.
 * It retrieves devices to monitor, sends collection requests through ZMQ,
 * processes responses, and stores metrics in the database.
 */
public class MetricsCollectionVerticle extends AbstractVerticle
{

    private static final Logger LOGGER = LoggerFactory.getLogger(MetricsCollectionVerticle.class);

    private static final long METRICS_INTERVAL_MS = 60000; // 1 minute interval
    private static final long REQUEST_TIMEOUT_MS = 180000; // 3 minutes timeout

    private PollingService pollingService;
    private DatabaseService dbService;
    private long timerMetricsId;
    private final ConcurrentMap<String, Promise<JsonObject>> pendingRequests = new ConcurrentHashMap<>();
    private final AtomicBoolean isCollecting = new AtomicBoolean(false);
    private final ConcurrentMap<String, Long> timeoutTimers = new ConcurrentHashMap<>();

    @Override
    public void start(Promise<Void> startPromise)
    {
        dbService = DatabaseService.getInstance();
        pollingService = new PollingService();

        // Set up event bus consumer for ZMQ responses
        setupEventBusConsumer();

        // Schedule periodic metrics collection
        scheduleMetricsCollection();

        startPromise.complete();
        LOGGER.info("MetricsCollectionVerticle started successfully");
    }

    @Override
    public void stop(Promise<Void> stopPromise)
    {
        if (timerMetricsId != 0)
        {
            vertx.cancelTimer(timerMetricsId);
        }

        // Cancel all pending timeout timers
        timeoutTimers.values().forEach(vertx::cancelTimer);
        timeoutTimers.clear();

        stopPromise.complete();
        LOGGER.info("MetricsCollectionVerticle stopped successfully");
    }

    private void setupEventBusConsumer()
    {
        // Register a consumer for the ZMQ responses based on pattern matching
        vertx.eventBus().consumer(ZMQCommunicationVerticle.EB_ZMQ_RESPONSE , message ->
        {
            var response = (JsonObject) message.body();
            String requestId = response.getString("request_id");

            LOGGER.info("Received ZMQ response for request ID: {}", requestId);

            // Complete the pending promise for this request ID
            if (requestId != null && pendingRequests.containsKey(requestId))
            {
                var promise = pendingRequests.<JsonObject>remove(requestId);
                if (promise != null)
                {
                    promise.complete(response);

                    // Cancel the timeout timer
                    var timerId = timeoutTimers.remove(requestId);
                    if (timerId != null)
                    {
                        vertx.cancelTimer(timerId);
                    }
                }
            }

            // If this is a metrics response, store it in the database
            if ("metrics".equals(response.getString("type")))
            {
                storeMetricsInDatabase(response);
            }

        });
    }

    private void scheduleMetricsCollection()
    {
        // Schedule the first collection after a short delay
        vertx.setTimer(1000, id -> collectMetrics());

        // Then schedule recurring collections
        timerMetricsId = vertx.setPeriodic(METRICS_INTERVAL_MS, id -> collectMetrics());

        LOGGER.info("Metrics collection scheduled every {} ms", METRICS_INTERVAL_MS);
    }

    private void collectMetrics()
    {
        // Avoid concurrent collection attempts
        if (isCollecting.compareAndSet(false, true))
        {
            LOGGER.info("Starting metrics collection cycle");

            // Get devices to monitor asynchronously
            pollingService.getDeviceToMonitor().onComplete(result ->
            {
                if (result.succeeded())
                {
                    var devices = result.result();
                    LOGGER.info("Devices to monitor: {}", devices.encodePrettily());

                    var provisions = devices.getJsonArray("provisions");
                    if (provisions != null && !provisions.isEmpty())
                    {
                        sendMetricsRequest(provisions);
                    }
                    else
                    {
                        LOGGER.info("No devices to monitor");
                        isCollecting.set(false);
                    }
                }
                else
                {
                    // Handle failure in fetching devices
                    LOGGER.error("Failed to fetch devices to monitor: {}", result.cause().getMessage());
                    isCollecting.set(false);
                }
            });
        }
        else
        {
            LOGGER.warn("Previous metrics collection still in progress, skipping this cycle");
        }
    }

    private void sendMetricsRequest(JsonArray provisions)
    {
        // Convert Vert.x JSON to ZMQ plugin format
        var batchInput = new JsonArray();

        for (var i = 0; i < provisions.size(); i++)
        {
            var provision = provisions.getJsonObject(i);
            LOGGER.info("Processing provision: {}", provision.encodePrettily());
//            var deviceInput = new JsonObject()
//                    .put("ip", provision.getString(Constants.DISC_IP_ADDRESS))
//                    .put("port", provision.getInteger(Constants.DISC_PORT_NO))
//                    .put("username", provision.getString(Constants.CRED_USERNAME))
//                    .put("password", provision.getString(Constants.CRED_PASSWORD))
//                    .put("protocol", provision.getString(Constants.CRED_PROTOCOL))
//                    .put("monitor_id", provision.getInteger(Constants.POLLING_MONITOR_ID));  // Include monitorId in the request

            batchInput.add(provision);
        }

        var requestId = UUID.randomUUID().toString();

        // Create the ZMQ request
        var request = new JsonObject()
                .put("request_id", requestId)
                .put("command", "metrics")
                .put("data", batchInput);

        LOGGER.info("Sending metrics request: {}", request.encodePrettily());

        var responsePromise = Promise.<JsonObject>promise();
        pendingRequests.put(requestId, responsePromise);

        // Set a timeout for the request
        long timeoutId = vertx.setTimer(REQUEST_TIMEOUT_MS, id ->
        {
            if (pendingRequests.remove(requestId) != null)
            {
                LOGGER.warn("Metrics request {} timed out", requestId);
                isCollecting.set(false);
                timeoutTimers.remove(requestId);
            }
        });

        timeoutTimers.put(requestId, timeoutId);

        // Send the request through the event bus to ZMQCommunicationVerticle
        vertx.eventBus().request(ZMQCommunicationVerticle.EB_ZMQ_SEND, request, reply ->
        {
            if (reply.succeeded())
            {
                LOGGER.info("ZMQ request sent successfully with ID: {}", requestId);
            }
            else
            {
                LOGGER.error("Failed to send ZMQ request: {}", reply.cause().getMessage());
                pendingRequests.remove(requestId);
                vertx.cancelTimer(timeoutId);
                timeoutTimers.remove(requestId);
                isCollecting.set(false);
            }
        });

        // Handle the response
        responsePromise.future().onComplete(result ->
        {
            if (result.succeeded())
            {
                LOGGER.info("Received metrics response for request ID: {}", requestId);
            }
            else
            {
                LOGGER.error("Failed to get metrics", result.cause());
            }

            // Allow new collection cycle
            isCollecting.set(false);
        });
    }

    private void storeMetricsInDatabase(JsonObject metricsResponse)
    {
        // Extract metrics from the response
        var metrics = metricsResponse.getJsonObject("metrics");

        if (metrics == null || metrics.isEmpty())
        {
            LOGGER.warn("No metrics found in response");
            return;
        }

        // Process each device's metrics
        metrics.forEach(entry ->
        {
            // Get the device metrics object
            var deviceMetrics = (JsonObject) entry.getValue();

            // Extract the monitorId from the device metrics
            // Assuming the Go plugin includes the monitorId in each device's metrics response
            var monitorId = deviceMetrics.getInteger("monitor_id");

            if (monitorId == null)
            {
                LOGGER.warn("No monitorId found in metrics for device: {}", entry.getKey());
                return;
            }

            // Insert the data into Polling_data table
            storePollingData(monitorId, deviceMetrics);
        });
    }

    private void storePollingData(Integer monitorId, JsonObject deviceMetrics)
    {
        var timestampStr = deviceMetrics.getString("timestamp");

        // Parse the timestamp String to a LocalDateTime object
        var timestamp = LocalDateTime.parse(timestampStr, DateTimeFormatter.ISO_DATE_TIME);

        // Store the entire metrics object as JSONB
        var params = new JsonObject()
                .put("monitor_id", monitorId)
                .put("data", deviceMetrics)
                .put("timestamp", timestamp.toString());

        // Execute the query
        pollingService.insertPollingData(params);
    }
}