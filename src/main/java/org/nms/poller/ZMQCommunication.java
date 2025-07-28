package org.nms.poller;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.nms.utils.ConfigLoader;
import org.nms.utils.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * ZMQCommunication Verticle responsible for ZeroMQ communication with the Go plugin.
 * This verticle handles socket initialization, message sending, and response receiving.
 */
public class ZMQCommunication extends AbstractVerticle
{

    private static final Logger LOGGER = LoggerFactory.getLogger(ZMQCommunication.class);

    private static final String PUSH_ENDPOINT =  ConfigLoader.get().getString("zmq.push.port");//"tcp://*:5555";
    private static final String PULL_ENDPOINT = ConfigLoader.get().getString("zmq.pull.port");

    private static final long POLL_INTERVAL_MS = 50; // Poll ZMQ socket every 50ms
    private static Process goProcess;

    // Event bus addresses
    public static final String EB_ZMQ_SEND = "zmq.send";
    public static final String EB_DISCOVERY_ZMQ_RESPONSE = "zmq.discovery.response";
    public static final String EB_METRICS_ZMQ_RESPONSE = "zmq.metrics.response";

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
        startGoPlugin()
                .compose(v ->initializeZmq())
                .onComplete(result ->
                {
                    if (result.succeeded())
                    {
                        // Start non-blocking polling of ZMQ socket
                        startListening();

                        // Set up event bus consumer for send requests
                        setupEventBusConsumer();

                        startPromise.complete();

                        LOGGER.info("ZMQCommunication Verticle started successfully");
                    }
                    else
                    {
                        startPromise.fail(result.cause());
                        LOGGER.error("Failed to initialize ZMQ: {}", result.cause().getMessage());
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

        // Stop Go plugin
        if (goProcess != null && goProcess.isAlive())
        {
            goProcess.destroy();
            try
            {
                // Wait up to 1 seconds for the process to terminate
                var terminated = goProcess.waitFor(1, TimeUnit.SECONDS);

                if (terminated)
                {
                    LOGGER.info("go_plugin terminated successfully");
                }
                else
                {
                    LOGGER.warn("go_plugin did not terminate within 5 seconds");

                    goProcess.destroyForcibly(); // Force termination

                    LOGGER.info("go_plugin forcibly terminated");
                }
            }
            catch (Exception exception)
            {
                LOGGER.warn("Interrupted while stopping go_plugin: {}", exception.getMessage());

                goProcess.destroyForcibly(); // Force termination on interrupt

            }
        }
        else
        {
            LOGGER.info("No go_plugin process running or already terminated");
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
                LOGGER.error("Error closing ZMQ resources", exception);

                promise.fail(exception);
            }
        }, result ->
        {
            stopPromise.complete();

            LOGGER.info("ZMQCommunicationVerticle stopped successfully");
        });
    }

    private  Future<Void> startGoPlugin()
    {
        return vertx.executeBlocking(promise ->
        {
            try
            {
                // Kill any existing instance
                try
                {
                    var killProcess = new ProcessBuilder("pkill", "-f", "plugin-zmq").start();
                    var exitCode = killProcess.waitFor(1, TimeUnit.SECONDS);

                    if (exitCode)
                    {
                        LOGGER.info("Killed existing go_plugin if any");
                    }
                    else
                    {
                        LOGGER.warn("No existing go_plugin found or failed to kill (exit code: {})", exitCode);
                    }
                }
                catch (Exception exception)
                {
                    LOGGER.warn("No existing go_plugin found or failed to kill: {}", exception.getMessage());
                }

                // Start new plugin
                var goPlugin = new File(ConfigLoader.get().getString("plugin.path"));

                if (!goPlugin.exists() || !goPlugin.canExecute())
                {
                    LOGGER.error("go_plugin not found or not executable at: {}", goPlugin.getAbsolutePath());
                    promise.fail("go_plugin not found or not executable");
                    return;
                }

                goProcess = new ProcessBuilder(goPlugin.getAbsolutePath()).start();

                // Verify process started successfully
                try
                {
                    if (goProcess.isAlive())
                    {
                        LOGGER.info("go_plugin started successfully and is running");
                        promise.complete();
                    }
                    else
                    {
                        LOGGER.error("go_plugin started but terminated prematurely");
                        promise.fail("go_plugin terminated prematurely");
                    }
                }
                catch (Exception exception)
                {
                    promise.fail("Interrupted while verifying go_plugin");
                }

            }
            catch (Exception exception)
            {
                LOGGER.error("Failed to start go_plugin: {}", exception.getMessage());

                promise.fail("Failed to start go_plugin: " + exception.getMessage());
            }
        });
    }

    private Future<Void> initializeZmq()
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
                pushSocket.bind(PUSH_ENDPOINT);

                // Socket to receive responses from the Go plugin (PULL)
                pullSocket = zmqContext.createSocket(SocketType.PULL);
                pullSocket.setRcvHWM(1000); // Set high water mark to prevent message loss
                pullSocket.setReceiveTimeOut(1);
                pullSocket.bind(PULL_ENDPOINT);

                zmqInitialized = true;
                blockingPromise.complete();
                LOGGER.info("ZMQ sockets initialized successfully");
            }
            catch (Exception exception)
            {
                LOGGER.error("Failed to initialize ZMQ sockets", exception);
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

        return promise.future();
    }

    private void setupEventBusConsumer()
    {
        // Register consumer for send requests
        sendConsumer = vertx.eventBus().consumer(EB_ZMQ_SEND, message ->
        {
            var request =  message.body();

            var requestId = request.getString(Constants.REQUEST_ID);
            var batchSize = request.getJsonArray(Constants.DATA, new JsonArray()).size();

            // Send the request to the Go plugin
            sendZmqMessage(request).onComplete(ar ->
            {
                if (ar.succeeded())
                {
                    LOGGER.info("Successfully sent ZMQ message with request ID: {}, batch size: {}", requestId, batchSize);
                }
                else
                {
                    LOGGER.error("Failed to send ZMQ message with request ID: {}", requestId, ar.cause());
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

        LOGGER.info("Started periodic polling of ZMQ socket every {} ms", POLL_INTERVAL_MS);
    }

    private void listenZmqSocket()
    {
        try
        {
            // Non-blocking poll of the ZMQ socket
            var responseBytes = pullSocket.recv(ZMQ.DONTWAIT);

            if (responseBytes != null && responseBytes.length > 0)
            {
                // Process the response on the event loop
                processResponse(new String(responseBytes));

            }
        }
        catch (Exception exception)
        {
            LOGGER.error("Error polling ZMQ socket", exception);
        }
    }

    private void processResponse(String responseJson)
    {
        try
        {
            LOGGER.debug("Processing ZMQ response: {}", responseJson);
            var body = new JsonObject(responseJson);

            if(body.getString(Constants.TYPE).equals(Constants.COMMAND_DISCOVERY))
            {
                vertx.eventBus().send(EB_DISCOVERY_ZMQ_RESPONSE, body);

                LOGGER.info("Sent response to event bus");
            }
            else
            {
                vertx.eventBus().send(EB_METRICS_ZMQ_RESPONSE, body);
            }

        }
        catch (Exception exception)
        {
            LOGGER.error("Error processing ZMQ response", exception);
        }
    }

    private Future<Void> sendZmqMessage(JsonObject message)
    {
        var promise = Promise.<Void>promise();
        try
        {
            pushSocket.send(message.encode().getBytes(), ZMQ.DONTWAIT);
            promise.complete();
        }
        catch (Exception exception)
        {
            promise.fail(exception);
        }

        return promise.future();
    }
}