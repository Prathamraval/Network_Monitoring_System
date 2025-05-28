package org.nms.poller;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.nms.service.PollingService;
import org.nms.utils.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class PollerEngine extends AbstractVerticle
{
    private static final Logger LOGGER = LoggerFactory.getLogger(PollerEngine.class);

    private static final int BATCH_SIZE = 50;

    private static final int POLLING_INTERVAL = 1000;
    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata"); // IST time zone

    private PollingService pollingService;

    private long timerMetricsId;

    private final HashMap<String, JsonArray> pendingRequests = new HashMap<>();

    private final HashMap<String, Set<Long>> respondedDevices = new HashMap<>();

    private final HashMap<String, Long> batchAvgWaitTimes = new HashMap<>(); // Store avg wait time per batch

    private final HashMap<String, Long> batchTimerIds = new HashMap<>(); // Store timer IDs for cancellation

    private String lastBatchId = null; // Track the last batch for batchAvgTime

    private final HashMap<Long, JsonObject> cache = new HashMap<>(); // Cache for provisions

    @Override
    public void start(Promise<Void> startPromise)
    {
        pollingService = new PollingService();

        setupEventBusConsumer();

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
        pendingRequests.clear();

        respondedDevices.clear();

        batchAvgWaitTimes.clear();

        batchTimerIds.clear();

        cache.clear();

        stopPromise.complete();

        LOGGER.info("MetricsCollectionVerticle stopped successfully");
    }

    private void setupEventBusConsumer()
    {
        vertx.eventBus().<JsonObject>consumer(ZMQCommunication.EB_ZMQ_RESPONSE, message ->
        {
            var response = message.body();

            LOGGER.debug("Response content: {}", response.encodePrettily());

            processResponse(response);
        });
    }

    private void processResponse(JsonObject response)
    {
        var requestId = response.getString(Constants.REQUEST_ID);
        var monitorId = response.getInteger(Constants.MONITOR_ID);

        if (requestId == null || monitorId == null)
        {
            LOGGER.warn("Invalid response: request_id or monitor_id missing");
            return;
        }

        // Mark device as responded
        respondedDevices.computeIfAbsent(requestId, k -> ConcurrentHashMap.newKeySet()).add(monitorId.longValue());

        // Update timestamp in PollingService cache
        var timestamp = response.getString(Constants.POLLING_TIMESTAMP, Instant.now().toString());
        pollingService.updateDeviceTimestamp(monitorId.longValue(), timestamp);

        // Store metrics in database
        if (Constants.COMMAND_METRICS.equals(response.getString(Constants.TYPE)))
        {
            storeMetricsInDatabase(response);
        }

        // Check if all devices in the batch have responded
        var batch = pendingRequests.get(requestId);

        if (batch != null)
        {
            var expectedSize = batch.size();

            var respondedSize = respondedDevices.getOrDefault(requestId, ConcurrentHashMap.newKeySet()).size();

            if (respondedSize >= expectedSize)
            {
                completeBatch(requestId, batch, System.currentTimeMillis());
            }
        }
    }

    private void completeBatch(String requestId, JsonArray batch, long batchStartTimeMs)
    {
        // Cancel the timer
        var timerId = batchTimerIds.remove(requestId);

        if (timerId != null)
        {
            vertx.cancelTimer(timerId);

            LOGGER.debug("Canceled timer for batch ID: {}", requestId);
        }

        // Process non-responding devices
        var responded = respondedDevices.getOrDefault(requestId, ConcurrentHashMap.newKeySet());
        var batchStartTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(batchStartTimeMs), IST_ZONE);

        if(responded.size() < pendingRequests.get(requestId).size())
        {
            for (var i = 0; i < batch.size(); i++)
            {
                var device = batch.getJsonObject(i);
                var monitorId = device.getLong(Constants.MONITOR_ID);

                if (!responded.contains(monitorId))
                {
                    LOGGER.warn("No response for device {} in request {}", monitorId, requestId);

                    var offsetDateTime = batchStartTime.atZone(IST_ZONE).toOffsetDateTime();

                    var deviceMetrics = new JsonObject()
                            .put(Constants.POLLING_TIMESTAMP, offsetDateTime.toString())
                            .put(Constants.ERROR, "device is not up");

                    storePollingData(monitorId.intValue(), deviceMetrics);

                    pollingService.updateDeviceTimestamp(monitorId, batchStartTime.toString());
                }
            }
        }

        // Clean up
        pendingRequests.remove(requestId);

        respondedDevices.remove(requestId);

        LOGGER.info("Batch {} completed", requestId);
    }

    private void scheduleMetricsCollection()
    {
        vertx.setTimer(POLLING_INTERVAL, id -> collectMetrics());
    }

    private Future<Object> collectMetrics()
    {
        return pollingService.getDeviceToMonitor().compose(devices ->
        {
            var provisions = devices.getJsonArray(Constants.PROVISIONS);

            LOGGER.info("Provisions: {}", provisions);

            if (provisions != null && !provisions.isEmpty())
            {
                return sendBatchedMetrics(provisions);
            }
            else
            {
                LOGGER.info("No devices to monitor");

                scheduleNextCollection();

                return Future.succeededFuture();
            }
        }).recover(error ->
        {
            LOGGER.error("Failed to fetch devices to monitor: {}", error.getMessage());

            scheduleNextCollection();

            return Future.succeededFuture();
        });
    }

    private Future<Object> sendBatchedMetrics(JsonArray provisions)
    {
        var totalDevices = provisions.size();

        LOGGER.info("Processing {} devices in batches of {}", totalDevices, BATCH_SIZE);

        var batchPromises = new ArrayList<Future<Object>>();

        long previousAvgWaitTimeMs = lastBatchId != null ? batchAvgWaitTimes.get(lastBatchId) : 0L;

        // Process all batches simultaneously
        for (var start = 0; start < totalDevices; start += BATCH_SIZE)
        {
            var batch = new JsonArray();
            var end = Math.min(start + BATCH_SIZE, totalDevices);
            long maxWaitTimeMs = 0;
            long sumWaitTimeMs = 0;

            // Build batch and calculate max wait time and sum of wait times
            for (var i = start; i < end; i++)
            {
                var provision = provisions.getJsonObject(i);

                var waitTimeSec = provision.getInteger(Constants.DISC_WAIT_TIME); // Default 5 seconds
                var waitTimeMs = waitTimeSec * 1000;

                sumWaitTimeMs += waitTimeMs;

                if (waitTimeMs > maxWaitTimeMs)
                {
                    maxWaitTimeMs = waitTimeMs;
                }

                batch.add(new JsonObject()
                        .put(Constants.MONITOR_ID, provision.getInteger(Constants.MONITOR_ID))
                        .put(Constants.DISC_IP_ADDRESS, provision.getString(Constants.DISC_IP_ADDRESS))
                        .put(Constants.DISC_PORT_NO, provision.getInteger(Constants.DISC_PORT_NO))
                        .put(Constants.CRED_USERNAME, provision.getString(Constants.CRED_USERNAME))
                        .put(Constants.CRED_PASSWORD, provision.getString(Constants.CRED_PASSWORD))
                        .put(Constants.CRED_PROTOCOL, provision.getString(Constants.CRED_PROTOCOL))
                        .put(Constants.STATUS, provision.getBoolean(Constants.STATUS, true))
                        .put(Constants.DISC_WAIT_TIME, waitTimeSec)); // Send wait_time to Go

            }

            // Calculate batch timeout
            long avgWaitTimeMs = batch.size() > 0 ? sumWaitTimeMs / batch.size() : 0;

            long batchTimeoutMs = (start == 0 ? maxWaitTimeMs : previousAvgWaitTimeMs + maxWaitTimeMs);

            var requestId = UUID.randomUUID().toString();

            // Store avg wait time for the next iteration
            batchAvgWaitTimes.put(requestId, avgWaitTimeMs);

            lastBatchId = requestId;

            batchPromises.add(sendMetricsRequest(batch, requestId, batchTimeoutMs));
        }

        // Wait for all batches to complete
        return CompositeFuture.all(batchPromises.stream().map(f -> (Future<?>) f).collect(Collectors.toList()))
                .mapEmpty()
                .onComplete(result -> scheduleNextCollection());
    }

    private Future<Object> sendMetricsRequest(JsonArray batch, String requestId, long batchTimeoutMs)
    {
        var request = new JsonObject()
                .put(Constants.REQUEST_ID, requestId)
                .put(Constants.COMMAND, Constants.COMMAND_METRICS)
                .put(Constants.DATA, batch);

        LOGGER.info("Sending metrics request for {} devices: {}, timeout: {}ms", batch.size(), requestId, batchTimeoutMs);

        pendingRequests.put(requestId, batch);

        long batchStartTimeMs = System.currentTimeMillis();
        long timerId = vertx.setTimer(batchTimeoutMs, id ->
        {
            if (pendingRequests.containsKey(requestId))
            {
                completeBatch(requestId, batch, batchStartTimeMs);
            }
        });
        batchTimerIds.put(requestId, timerId); // Store timer ID

        return vertx.eventBus().<JsonObject>request(ZMQCommunication.EB_ZMQ_SEND, request)
                .compose(reply ->
                {
                    LOGGER.info("ZMQ request sent successfully with ID: {}", requestId);
                    return Future.succeededFuture(); // No responsePromise needed
                })
                .recover(error ->
                {
                    LOGGER.error("Failed to send ZMQ request: {}", error.getMessage());
                    completeBatch(requestId, batch, batchStartTimeMs);
                    return Future.succeededFuture();
                });
    }

    private void scheduleNextCollection()
    {
        if (timerMetricsId != 0)
        {
            vertx.cancelTimer(timerMetricsId);
        }

        timerMetricsId = vertx.setTimer(POLLING_INTERVAL, id -> collectMetrics());

        LOGGER.info("Scheduled next metrics collection in 1000 ms");
    }

    private void storeMetricsInDatabase(JsonObject metricsResponse)
    {
        var metrics = metricsResponse.getJsonObject(Constants.COMMAND_METRICS);

        if (metrics == null || metrics.isEmpty())
        {
            LOGGER.warn("No metrics found in response for request ID: {}", metricsResponse.getString(Constants.REQUEST_ID));
            return;
        }

        var monitorId = metricsResponse.getInteger(Constants.MONITOR_ID);
        if (monitorId == null)
        {
            LOGGER.warn("No monitor_id found in response for request ID: {}", metricsResponse.getString("request_id"));
            return;
        }

        storePollingData(monitorId, metrics);
    }

    private void storePollingData(Integer monitorId, JsonObject deviceMetrics)
    {
        var timestampStr = deviceMetrics.getString(Constants.POLLING_TIMESTAMP);

        if (timestampStr == null)
        {
            LOGGER.warn("No timestamp in device metrics for monitor ID: {}", monitorId);
            timestampStr = Instant.now().toString();
        }

        LocalDateTime timestamp;
        try
        {
            var offsetDateTime = OffsetDateTime.parse(timestampStr);
            timestamp = offsetDateTime.toLocalDateTime();
        }
        catch (Exception exception)
        {
            LOGGER.error("Failed to parse timestamp: {} for monitor ID: {}", timestampStr, monitorId, exception);

            timestamp = LocalDateTime.ofInstant(Instant.now(), IST_ZONE);
        }

        var params = new JsonObject()
                .put(Constants.MONITOR_ID, monitorId)
                .put(Constants.DATA, deviceMetrics)
                .put(Constants.POLLING_TIMESTAMP, timestamp.toString());

        pollingService.insertPollingData(params).onComplete(result ->
        {
            if (result.succeeded())
            {
                LOGGER.info("Stored metrics for monitor ID: {}", monitorId);
            }
            else
            {
                LOGGER.error("Failed to store metrics for monitor ID: {}", monitorId, result.cause());
            }
        });
    }
}