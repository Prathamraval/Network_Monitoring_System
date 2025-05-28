package org.nms.poller;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.nms.utils.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

/**
 * Verticle responsible for ZeroMQ communication with the Go plugin.
 * This verticle handles socket initialization, message sending, and response receiving.
 */
public class ZMQCommunication extends AbstractVerticle
{

    private static final Logger logger = LoggerFactory.getLogger(ZMQCommunication.class);

    private static final String PUSH_ENDPOINT = "tcp://localhost:5555";
    private static final String PULL_ENDPOINT = "tcp://*:5556";
    private static final long POLL_INTERVAL_MS = 50; // Poll ZMQ socket every 50ms

    // Event bus addresses
    public static final String EB_ZMQ_SEND = "zmq.send";
    public static final String EB_ZMQ_RESPONSE = "zmq.response";

    private ZContext zmqContext;
    private ZMQ.Socket pushSocket;
    private ZMQ.Socket pullSocket;
    private long timerPollId;
    private boolean zmqInitialized = false;
    private MessageConsumer<JsonObject> sendConsumer;

    @Override
    public void start(Promise<Void> startPromise)
    {
        // Initialize ZMQ sockets
        initializeZmq().future().onComplete(result ->
        {
            if (result.succeeded())
            {
                // Start non-blocking polling of ZMQ socket
                startListening();

                // Set up event bus consumer for send requests
                setupEventBusConsumer();

                startPromise.complete();

                logger.info("ZMQCommunication Verticle started successfully");
            }
            else
            {
                startPromise.fail(result.cause());
                logger.error("Failed to initialize ZMQ: {}", result.cause().getMessage());
            }
        });
    }

    @Override
    public void stop(Promise<Void> stopPromise)
    {
        if (timerPollId != 0)
        {
            vertx.cancelTimer(timerPollId);
        }

        if (sendConsumer != null)
        {
            sendConsumer.unregister();
        }

        // Close ZMQ resources
        vertx.executeBlocking(promise ->
        {
            try
            {
                if (pushSocket != null)
                {
                    pushSocket.close();
                }

                if (pullSocket != null)
                {
                    pullSocket.close();
                }

                if (zmqContext != null)
                {
                    zmqContext.close();
                }

                promise.complete();
            }
            catch (Exception exception)
            {
                logger.error("Error closing ZMQ resources", exception);

                promise.fail(exception);
            }
        }, result ->
        {
            stopPromise.complete();

            logger.info("ZMQCommunicationVerticle stopped successfully");
        });
    }

    private Promise<Void> initializeZmq()
    {
        var promise = Promise.<Void>promise();

        // Run ZMQ operations on a separate thread to not block the event loop
        vertx.executeBlocking(blockingPromise ->
        {
            try
            {
                zmqContext = new ZContext();

                // Socket to send requests to the Go plugin (PUSH)
                pushSocket = zmqContext.createSocket(SocketType.PUSH);
                pushSocket.setSndHWM(1000); // Set high water mark to prevent message loss
                pushSocket.connect(PUSH_ENDPOINT);

                // Socket to receive responses from the Go plugin (PULL)
                pullSocket = zmqContext.createSocket(SocketType.PULL);
                pullSocket.setRcvHWM(1000); // Set high water mark to prevent message loss
                pullSocket.setReceiveTimeOut(1);
                pullSocket.bind(PULL_ENDPOINT);

                zmqInitialized = true;
                blockingPromise.complete();
                logger.info("ZMQ sockets initialized successfully");
            }
            catch (Exception exception)
            {
                logger.error("Failed to initialize ZMQ sockets", exception);
                blockingPromise.fail(exception);
            }
        }, result ->
        {
            if (result.succeeded())
            {
                promise.complete();
            }
            else
            {
                promise.fail(result.cause());
            }
        });

        return promise;
    }

    private void setupEventBusConsumer()
    {
        // Register consumer for send requests
        sendConsumer = vertx.eventBus().<JsonObject>consumer(EB_ZMQ_SEND, message ->
        {
            var request =  message.body();

            var requestId = request.getString(Constants.REQUEST_ID);
            var batchSize = request.getJsonArray(Constants.DATA, new JsonArray()).size();

            logger.info("Received request to send via ZMQ: {}, batch size: {}", requestId, batchSize);

            // Send the request to the Go plugin
            sendZmqMessage(request).future().onComplete(ar ->
            {
                if (ar.succeeded())
                {
                    message.reply(new JsonObject().put(Constants.STATUS, "sent").put(Constants.REQUEST_ID, requestId));
                    logger.info("Successfully sent ZMQ message with request ID: {}, batch size: {}", requestId, batchSize);
                }
                else
                {
                    message.fail(500, ar.cause().getMessage());
                    logger.error("Failed to send ZMQ message with request ID: {}", requestId, ar.cause());
                }
            });
        });
    }

    private void startListening()
    {

        // Use periodic timer to poll ZMQ socket instead of blocking loop
        timerPollId = vertx.setPeriodic(POLL_INTERVAL_MS, id ->
        {
            if (zmqInitialized)
            {
                listenZmqSocket();
            }
        });

        logger.info("Started periodic polling of ZMQ socket every {} ms", POLL_INTERVAL_MS);
    }

    private void listenZmqSocket()
    {
        vertx.executeBlocking(promise ->
        {
            try
            {
                // Non-blocking poll of the ZMQ socket
                var responseBytes = pullSocket.recv(ZMQ.DONTWAIT);

                if (responseBytes != null && responseBytes.length > 0)
                {
                    var responseJson = new String(responseBytes);

                    // Process the response on the event loop
                    vertx.runOnContext(v -> processResponse(responseJson));

                    promise.complete(true); // Indicate message was received
                }
                else
                {
                    promise.complete(false); // No message received
                }
            }
            catch (Exception exception)
            {
                logger.error("Error polling ZMQ socket", exception);
                promise.fail(exception);
            }
        }, false, result ->
        {
            if (result.failed())
            {
                logger.warn("ZMQ polling failed: {}", result.cause().getMessage());
            }
        });
    }

    private void processResponse(String responseJson)
    {
        try
        {
            logger.debug("Processing ZMQ response: {}", responseJson);

            var response = new JsonObject(responseJson);
            var requestId = response.getString(Constants.REQUEST_ID);

            if (requestId != null)
            {
                // Publish the response to the event bus
                vertx.eventBus().publish(EB_ZMQ_RESPONSE, response);
                logger.info("Published response for request ID: {} to event bus", requestId);
            }
            else
            {
                logger.warn("Response has no request_id: {}", responseJson);
            }
        }
        catch (Exception exception)
        {
            logger.error("Error processing ZMQ response", exception);
        }
    }

    private Promise<Void> sendZmqMessage(JsonObject message)
    {
        var promise = Promise.<Void>promise();

        // Execute on a worker thread to avoid blocking the event loop
        vertx.executeBlocking(p ->
        {
            try
            {
                pushSocket.send(message.encode().getBytes(), 0); // Use 0 flag for non-blocking
                p.complete();
            }
            catch (Exception exception)
            {
                p.fail(exception);
            }
        }, false, result ->
        {
            if (result.succeeded())
            {
                promise.complete();
            }
            else
            {
                promise.fail(result.cause());
            }
        });

        return promise;
    }
}