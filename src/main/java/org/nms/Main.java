package org.nms;

import io.vertx.core.Future;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import org.nms.modular.MetricsCollectionVerticle;
import org.nms.routerController.HttpVerticle;
import org.nms.database.DatabaseVerticle;
//import org.nms.modular.MetricsCollectionVerticle;
import org.nms.modular.ZMQCommunicationVerticle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class Main
{
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);
    private static final Vertx VERTX = Vertx.vertx();

    // Track deployed components
    private static final Map<String, String> deployedVerticles = new HashMap<>();
    private static Process goProcess;

    public static Vertx getVertx()
    {
        return VERTX;
    }

    public static void main(String[] args)
    {
        ensureLogDirectoryExists();
        // Setup shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown()));

        // Deploy components in sequence with simple error handling
        deployVerticle(new DatabaseVerticle(), "DATABASE")
                .compose(dbVerticleId ->
                {
                    deployedVerticles.put("DATABASE", dbVerticleId);
                    return deployVerticle(new HttpVerticle(), "HTTP");
                })
                .compose(httpVerticleId ->
                {
                    deployedVerticles.put("HTTP", httpVerticleId);
                    return startGoPlugin();
                })
                .compose(v -> deployVerticle(new ZMQCommunicationVerticle(), "ZMQ"))
                .compose(zmqVerticleId ->
                {
                    deployedVerticles.put("ZMQ", zmqVerticleId);
                    // For class-based deployment
                    return deployVerticleClass(MetricsCollectionVerticle.class, "METRICS");
                })
                .compose(metricsVerticleId ->
                {
                    deployedVerticles.put("METRICS", metricsVerticleId);
                    LOGGER.info("Application started successfully");
                    return Future.succeededFuture();
                })
                .onFailure(error ->
                {
                    LOGGER.error("Error during startup: {}", error.getMessage());
                    shutdown();
                });
    }

    private static void ensureLogDirectoryExists() {
        var logDir = new File("logs");
        if (!logDir.exists()) {
            boolean created = logDir.mkdirs();
            if (created) {
                System.out.println("Created logs directory");
            } else {
                System.err.println("Failed to create logs directory");
            }
        }
    }

    // Deploy a verticle instance
    private static Future<String> deployVerticle(Verticle verticle, String name)
    {
        LOGGER.info("Deploying {} verticle", name);

        return VERTX.deployVerticle(verticle)
                .onSuccess(id ->
                        LOGGER.info("{} verticle deployed with ID {}", name, id)
                )
                .onFailure(err ->
                        LOGGER.error("{} verticle deployment failed: {}", name, err.getMessage())
                );
    }

    // Deploy a verticle class
    private static Future<String> deployVerticleClass(Class<? extends Verticle> verticleClass, String name)
    {
        LOGGER.info("Deploying {} verticle from class", name);

        return VERTX.deployVerticle(verticleClass.getName())
                .onSuccess(id ->
                        LOGGER.info("{} verticle deployed with ID {}", name, id)
                )
                .onFailure(err ->
                        LOGGER.error("{} verticle deployment failed: {}", name, err.getMessage())
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
                    new ProcessBuilder("pkill", "-f", "plugin-zmq").start().waitFor();

                    LOGGER.info("Killed existing go_plugin if any");
                }
                catch (Exception exception)
                {
                    LOGGER.warn("No existing go_plugin found or failed to kill: {}", exception.getMessage());
                }

                // Start new plugin
                var projectDir = System.getProperty("user.dir");
                var goPlugin = new File(projectDir + "/modular-plugin/plugin-zmq");

                if (!goPlugin.exists() || !goPlugin.canExecute())
                {
                    throw new IllegalStateException("go_plugin not found or not executable at: " + goPlugin.getAbsolutePath());
                }

                goProcess = new ProcessBuilder(goPlugin.getAbsolutePath(), "zmq").start();
                LOGGER.info("go_plugin started successfully");

                // Auto-cleanup on JVM shutdown is handled by the main shutdown hook
                promise.complete();
            }
            catch (Exception exception)
            {
                LOGGER.error("Failed to start go_plugin: {}", exception.getMessage());
                promise.fail(exception);
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

            LOGGER.info("go_plugin terminated");
        }

        // Undeploy verticles
        for (Map.Entry<String, String> entry : deployedVerticles.entrySet())
        {
            var name = entry.getKey();
            var id = entry.getValue();
            LOGGER.info("Undeploying {} verticle", name);

            VERTX.undeploy(id)
                    .onSuccess(v ->
                            LOGGER.info("{} verticle undeployed", name)
                    )
                    .onFailure(err ->
                            LOGGER.warn("Failed to undeploy {} verticle: {}", name, err.getMessage())
                    );
        }

        // Give some time for undeployment to complete, then close Vertx
        VERTX.setTimer(2000, id ->
        {
            LOGGER.info("Closing Vert.x");

            VERTX.close()
                    .onSuccess(v ->
                            LOGGER.info("Vert.x closed successfully")
                    )
                    .onFailure(err ->
                            LOGGER.error("Error closing Vert.x: {}", err.getMessage())
                    );
        });
    }
}