package org.nms;

import io.vertx.core.Future;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import org.nms.discovery.DiscoveryVerticle;
import org.nms.endPoints.Server;
import org.nms.database.DatabaseVerticle;
import org.nms.poller.ZMQCommunication;
import org.nms.poller.PollerEngine;
import org.nms.utils.ConfigLoader;
import org.nms.utils.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class Bootstrap
{
    static
    {
        ConfigLoader.init(Constants.CONFIG_FILE_PATH);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(Bootstrap.class);

    private static final Vertx VERTX = Vertx.vertx();

    private static Process goProcess;

    public static Vertx getVertx()
    {
        return VERTX;
    }

    /*
        * Main entry point for the application.
        * This method initializes the Vert.x instance, deploys necessary verticles,
        * starts the Go plugin, and sets up the shutdown hook.
     */

    public static void main(String[] args)
    {
        Runtime.getRuntime().addShutdownHook(new Thread(Bootstrap::shutdown));

        deployVerticle(new DatabaseVerticle(), Constants.DATABASE)
                .compose(id -> deployVerticle(new Server(), Constants.HTTP))
                .compose(id -> startGoPlugin())
                .compose(id -> deployVerticle(new ZMQCommunication(), Constants.ZMQ))
                .compose(id -> deployVerticle(new DiscoveryVerticle(), Constants.ZMQ))
                .compose(id -> deployVerticle(new PollerEngine(), Constants.METRICS))
                .onSuccess(v -> LOGGER.info("Application started successfully"))
                .onFailure(error ->
                {
                    LOGGER.error("Error during startup: {}", error.getMessage());

                    shutdown();
                });
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

    public static void shutdown()
    {
        LOGGER.info("Shutting down application...");

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

        // Undeploy all Verticles
        var deployedVerticleIds = getDeployedVerticleIds();

        LOGGER.info("Undeploying verticles with IDs: {}", deployedVerticleIds);

        if (deployedVerticleIds.isEmpty())
        {
            LOGGER.info("Application shutdown complete");

            VERTX.close().onComplete(v -> LOGGER.info("Vert.x instance closed"));

            return;
        }

        for (var id : deployedVerticleIds)
        {
             VERTX.undeploy(id);
        }

        LOGGER.info("Application shutdown complete");
    }

    // Utility method to get deployed Verticle IDs at runtime
    public static Set<String> getDeployedVerticleIds()
    {
        return VERTX.deploymentIDs();
    }
}