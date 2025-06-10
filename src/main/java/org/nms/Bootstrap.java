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

    public static void shutdown()
    {
        LOGGER.info("Shutting down application...");


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