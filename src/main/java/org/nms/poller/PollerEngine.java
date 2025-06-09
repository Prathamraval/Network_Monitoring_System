package org.nms.poller;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
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
import java.time.format.DateTimeParseException;
import java.util.*;

public class PollerEngine extends AbstractVerticle
{
    private static final Logger LOGGER = LoggerFactory.getLogger(PollerEngine.class);

    private static final int BATCH_SIZE = 50; // Reduced to match Go plugin

    private static final long BATCH_BUFFER_TIME = 1000; // 1 seconds

    private static final long POLLING_CHECK_INTERVAL_MS = 5000; // 5 seconds

    private PollingService pollingService;

    private long timerMetricsId;

    private final HashMap<String, JsonArray> pendingRequests = new HashMap<>(); //requestId to JsonArray of devices

    private final HashMap<String, List<Long>> respondedDevices = new HashMap<>();// requestId to list of monitor_ids that responded

    private final HashMap<String, Long> batchAvgWaitTimes = new HashMap<>();// requestId to average wait time in ms

    private final HashMap<String, Long> batchTimerIds = new HashMap<>();

    private final Set<Long> pendingDevices = new HashSet<>() ; // monitor_id to request_id

    private String lastBatchId = null;


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
        pendingDevices.clear();
        stopPromise.complete();
        LOGGER.info("MetricsCollectionVerticle stopped successfully");
    }

    private void setupEventBusConsumer()
    {
        vertx.eventBus().<JsonObject>localConsumer(ZMQCommunication.EB_METRICS_ZMQ_RESPONSE, message ->
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

        LOGGER.info("Processing response for request_id: {}, monitor_id: {}", requestId, monitorId);

        if (requestId.isEmpty() || monitorId == null)
        {
            LOGGER.warn("Invalid response: request_id or monitor_id missing");
            return;
        }

        respondedDevices.computeIfAbsent(requestId, k -> new ArrayList<>()).add(monitorId.longValue());

        LOGGER.info("Responded devices-------- {}", respondedDevices.get(requestId));

        var timestamp = response.getString(Constants.POLLING_TIMESTAMP, OffsetDateTime.now(Constants.IST_ZONE).toString());

        pollingService.updateDeviceTimestamp(monitorId.longValue(), timestamp);

        if (Constants.COMMAND_METRICS.equals(response.getString(Constants.TYPE)))
        {
            storeMetricsInDatabase(response);
        }

        var batch = pendingRequests.get(requestId);

        if (batch != null)
        {
            var expectedSize = batch.size();
            var respondedSize = respondedDevices.get(requestId).size();
            if (respondedSize >= expectedSize)
            {
                LOGGER.info("All devices responded for batch ID: {}", requestId);
                completeBatch(requestId, batch, System.currentTimeMillis());
            }
        }
    }

    private void completeBatch(String requestId, JsonArray batch, long batchStartTimeMs)
    {
        var timerId = batchTimerIds.remove(requestId);
        if (timerId != null)
        {
            vertx.cancelTimer(timerId);
            LOGGER.debug("Canceled timer for batch ID: {}", requestId);
        }

        var responded = respondedDevices.get(requestId);
        var batchStartTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(batchStartTimeMs), Constants.IST_ZONE);

        if (responded.size() < batch.size())
        {
            LOGGER.warn("Not all devices responded for batch ID: {}. Expected: {}, Responded: {} ,responded deivce :{}", requestId, batch.size(), responded.size(), responded);
            for (var i = 0; i < batch.size(); i++)
            {
                var device = batch.getJsonObject(i);
                var monitorId = device.getLong(Constants.MONITOR_ID);

                if (!responded.contains(monitorId))
                {
                    LOGGER.warn("No response for device {} in request {}", monitorId, requestId);

                    var offsetDateTime = batchStartTime.atZone(Constants.IST_ZONE).toOffsetDateTime();

                    var deviceMetrics = new JsonObject()
                            .put(Constants.POLLING_TIMESTAMP, offsetDateTime.toString())
                            .put(Constants.ERROR, "device is not up");

                    storePollingData(monitorId.intValue(), deviceMetrics);

                    pollingService.updateDeviceTimestamp(monitorId, offsetDateTime.toString());
                }
            }
        }

        // Remove devices from pendingDevices
        for (var i = 0; i < batch.size(); i++)
        {
            var device = batch.getJsonObject(i);
            pendingDevices.remove(device.getLong(Constants.MONITOR_ID));
        }

        pendingRequests.remove(requestId);
        respondedDevices.remove(requestId);

        LOGGER.info("Batch {} completed", requestId);
    }

    private void scheduleMetricsCollection()
    {
        vertx.setTimer(POLLING_CHECK_INTERVAL_MS, id -> collectMetrics());
    }

    private void collectMetrics()
    {
        pollingService.getDeviceToMonitor(pendingDevices).compose(devices ->
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
        try
        {
            var totalDevices = provisions.size();

            LOGGER.info("Last batch ID: {}", lastBatchId);


            for (var start = 0; start < totalDevices; start += BATCH_SIZE)
            {
                var batch = new JsonArray();
                var end = Math.min(start + BATCH_SIZE, totalDevices);
                long maxWaitTimeMs = 0;
                long sumWaitTimeMs = 0;
                long previousAvgWaitTimeMs = lastBatchId != null ? batchAvgWaitTimes.get(lastBatchId) : 0L;

                for (var i = start; i < end; i++)
                {
                    var provision = provisions.getJsonObject(i);

                    var waitTimeSec =provision.getInteger(Constants.DISC_WAIT_TIME);
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

                long avgWaitTimeMs = !batch.isEmpty() ? sumWaitTimeMs / batch.size() : 0;
                long batchTimeoutMs = (start == 0 ? maxWaitTimeMs : (previousAvgWaitTimeMs + maxWaitTimeMs))+BATCH_BUFFER_TIME ;

                LOGGER.info("Batch {}: size={}, avgWaitTimeMs={}, batchTimeoutMs={} previousAvgWaitTimeMs= {}", start / BATCH_SIZE, batch.size(), avgWaitTimeMs, batchTimeoutMs, previousAvgWaitTimeMs);
                var requestId = UUID.randomUUID().toString();

                batchAvgWaitTimes.put(requestId, previousAvgWaitTimeMs + avgWaitTimeMs);
                lastBatchId = requestId;

                sendMetricsRequest(batch, requestId, batchTimeoutMs);
            }

            scheduleNextCollection();
            return Future.succeededFuture();
        }
        catch (Exception e)
        {
            LOGGER.error("Error in sendBatchedMetrics: {}", e.getMessage());
            return Future.failedFuture(e);
        }

    }

    private void sendMetricsRequest(JsonArray batch, String requestId, long batchTimeoutMs)
    {
        var request = new JsonObject()
                .put(Constants.REQUEST_ID, requestId)
                .put(Constants.COMMAND, Constants.COMMAND_METRICS)
                .put(Constants.DATA, batch);

        LOGGER.info("Sending metrics request for {} devices: {}, timeout: {}ms", batch.size(), requestId, batchTimeoutMs);

        pendingRequests.put(requestId, batch);

        // Add devices to pendingDevices
        for (var i = 0; i < batch.size(); i++)
        {
            var device = batch.getJsonObject(i);
            pendingDevices.add(device.getLong(Constants.MONITOR_ID));
        }

        long batchStartTimeMs = System.currentTimeMillis();
        long timerId = vertx.setTimer(batchTimeoutMs, id ->
        {
            if (pendingRequests.containsKey(requestId))
            {
                LOGGER.info("Timer expired for batch ID: {}", requestId);
                completeBatch(requestId, batch, batchStartTimeMs);
            }
        });
        batchTimerIds.put(requestId, timerId);

         vertx.eventBus().<JsonObject>send(ZMQCommunication.EB_ZMQ_SEND, request);
    }

    private void scheduleNextCollection()
    {
        if (timerMetricsId != 0)
        {
            vertx.cancelTimer(timerMetricsId);
        }

        timerMetricsId = vertx.setTimer(POLLING_CHECK_INTERVAL_MS, id -> collectMetrics());
        LOGGER.info("Scheduled next metrics collection in {} ms", POLLING_CHECK_INTERVAL_MS);
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
            LOGGER.warn("No monitor_id found in response for request ID: {}", metricsResponse.getString(Constants.REQUEST_ID));
            return;
        }
        storePollingData(monitorId, metrics);
    }

    private void storePollingData(Integer monitorId, JsonObject deviceMetrics)
    {
        var timestampStr = deviceMetrics.getString(Constants.POLLING_TIMESTAMP);

        if (timestampStr == null) {
            LOGGER.warn("No timestamp in device metrics for monitor ID: {}", monitorId);
            timestampStr = OffsetDateTime.now(Constants.IST_ZONE).toString();
        }

        LocalDateTime timestamp;
        try {
            var offsetDateTime = OffsetDateTime.parse(timestampStr);
            timestamp = offsetDateTime.toLocalDateTime();
        } catch (DateTimeParseException e1) {
            try {
                timestamp = LocalDateTime.parse(timestampStr);
            } catch (DateTimeParseException e2) {
                LOGGER.error("Failed to parse timestamp: {} for monitor ID: {}", timestampStr, monitorId, e2);
                timestamp = LocalDateTime.ofInstant(Instant.now(), Constants.IST_ZONE);
            }
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
            } else
            {
                LOGGER.error("Failed to store metrics for monitor ID: {}", monitorId, result.cause());
            }
        });
    }
}