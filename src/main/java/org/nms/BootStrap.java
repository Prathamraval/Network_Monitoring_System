package org.nms;

import io.vertx.core.Future;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.CompositeFuture;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.nms.database.queries.ProvisionQueries;
import org.nms.endPoints.ApiResponse;
import org.nms.endPoints.HttpVerticle;
import org.nms.database.DatabaseVerticle;
import org.nms.poller.ZMQCommunication;
import org.nms.poller.PollerEngine;
import org.nms.service.PollingService;
import org.nms.utils.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class BootStrap
{
    private static final Logger LOGGER = LoggerFactory.getLogger(BootStrap.class);
    private static final Vertx VERTX = Vertx.vertx();
    private static Process goProcess;
    private static final PollingService pollingService = new PollingService(); // Create instance

    public static Vertx getVertx()
    {
        return VERTX;
    }

    public static void main(String[] args)
    {
        // Setup shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(BootStrap::shutdown));

        // Deploy components in sequence
        deployVerticle(new DatabaseVerticle(), Constants.DATABASE)
                .compose(id -> deployVerticle(new HttpVerticle(), Constants.HTTP))
                .compose(id -> startGoPlugin())
                .compose(id -> storeProvisionCache())
                .compose(id -> deployVerticle(new ZMQCommunication(), Constants.ZMQ))
                .compose(id -> deployVerticle(new PollerEngine(), Constants.METRICS))
                .onSuccess(v -> LOGGER.info("Application started successfully"))
                .onFailure(error ->
                {
                    LOGGER.error("Error during startup: {}", error.getMessage());

                    shutdown();
                });
    }

    private static Future<JsonObject> storeProvisionCache()
    {
        try
        {
            var dbRequest = new JsonObject()
                    .put(Constants.DB_QUERY, ProvisionQueries.SELECT_ALL_STATUS_TRUE_PROVISIONS);

            return VERTX.eventBus().<JsonObject>request(Constants.DB_EXECUTE_WITHOUT_PARAM_EVENTBUS, dbRequest)
                    .compose(reply ->
                    {
                        var rows = reply.body();
                        var provisionList = new JsonArray();

                        var rowsArray = rows.getJsonArray(Constants.ROWS, new JsonArray());
                        for (var i = 0; i < rowsArray.size(); i++)
                        {
                            try
                            {
                                var row = rowsArray.getJsonObject(i);
                                var provision = new JsonObject()
                                        .put(Constants.MONITOR_ID, row.getInteger(Constants.MONITOR_ID))
                                        .put(Constants.DISC_IP_ADDRESS, row.getString(Constants.DISC_IP_ADDRESS))
                                        .put(Constants.DISC_PORT_NO, row.getInteger(Constants.DISC_PORT_NO))
                                        .put(Constants.CRED_USERNAME, row.getString(Constants.CRED_USERNAME))
                                        .put(Constants.CRED_PASSWORD, row.getString(Constants.CRED_PASSWORD))
                                        .put(Constants.CRED_PROTOCOL, row.getString(Constants.CRED_PROTOCOL))
                                        .put(Constants.STATUS, row.getBoolean(Constants.PROVISION_STATUS, true))
                                        .put(Constants.DISC_WAIT_TIME,row.getInteger(Constants.DISC_WAIT_TIME))
                                        .put(Constants.LAST_POOL, row.getString(Constants.LAST_POOL));

                                provisionList.add(provision);
                                pollingService.cache.put(row.getLong(Constants.MONITOR_ID), provision);
                            }
                            catch (Exception exception)
                            {
                                LOGGER.error("Error processing row {}: {}", i, exception.getMessage());
                            }
                        }

                        LOGGER.info("Cached {} devices to monitor", pollingService.cache.size());

                        return Future.succeededFuture(new JsonObject().put("provisions", provisionList));
                    })
                    .recover(error ->
                    {
                        LOGGER.error("Failed to fetch devices: {}", error.getMessage());

                        return Future.succeededFuture(ApiResponse.error(500, "Failed to fetch devices: " + error.getMessage()).toJson());
                    });

        }
        catch (Exception exception)
        {
            LOGGER.error("Error in getDeviceToMonitor: {}", exception.getMessage());
            return Future.succeededFuture(ApiResponse.error(500, "Error fetching devices: " + exception.getMessage()).toJson());
        }
    }

    // Unified method to deploy a Verticle instance
    private static Future<String> deployVerticle(Verticle verticle, String name)
    {
        LOGGER.info("Deploying {} verticle", name);

        return VERTX.deployVerticle(verticle)
                .onSuccess(id ->
                        LOGGER.info("{} verticle deployed with ID {}", name, id)
                )
                .onFailure(error ->
                        LOGGER.error("{} verticle deployment failed: {}", name, error.getMessage())
                );
    }

    private static Future<Void> startGoPlugin()
    {
        return VERTX.executeBlocking(promise ->
        {
            try
            {
                // Kill any existing instance
                try
                {
                    var killProcess = new ProcessBuilder("pkill", "-f", "plugin-zmq").start();
                    var exitCode = killProcess.waitFor();

                    if (exitCode == 0)
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
                var projectDir = System.getProperty("user.dir");
                var goPlugin = new File(projectDir + "/plugin-final/plugin-zmq");

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

    public static void shutdown()
    {
        LOGGER.info("Shutting down application...");

        // Stop Go plugin
        if (goProcess != null && goProcess.isAlive())
        {
            goProcess.destroy();
            try
            {
                // Wait up to 5 seconds for the process to terminate
                var terminated = goProcess.waitFor(5, TimeUnit.SECONDS);
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

                LOGGER.info("go_plugin forcibly terminated due to interruption");
            }
        }
        else
        {
            LOGGER.info("No go_plugin process running or already terminated");
        }

        // Undeploy all Verticles
        var deployedVerticleIds = getDeployedVerticleIds();

        var undeployFutures = new ArrayList<Future>();

        for (var id : deployedVerticleIds)
        {
            undeployFutures.add(
                    VERTX.undeploy(id)
                            .onSuccess(v -> LOGGER.info("Verticle with ID {} undeployed", id))
                            .onFailure(err -> LOGGER.warn("Failed to undeploy verticle with ID {}: {}", id, err.getMessage()))
            );
        }
        if (undeployFutures.isEmpty())
        {
            LOGGER.info("No verticles to undeploy");
        }
        else
        {
            LOGGER.info("Undeploying {} verticles", undeployFutures.size());
        }
        // Wait for all Verticles to undeploy
        CompositeFuture.all(undeployFutures)
                .onComplete(result ->
                {
                    LOGGER.info("All verticles undeployed");

                    // Close Vert.x
                    VERTX.close()
                            .onSuccess(v -> LOGGER.info("Vert.x closed successfully"))
                            .onFailure(error ->
                                    LOGGER.error("Error closing Vert.x: {}", error.getMessage())
                            );
                });

        LOGGER.info("Application shutdown complete");
    }

    // Utility method to get deployed Verticle IDs at runtime
    public static Set<String> getDeployedVerticleIds()
    {
        return VERTX.deploymentIDs();
    }
}