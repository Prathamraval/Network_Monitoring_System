package org.nms.endPoints;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.ext.web.Router;
import org.nms.endPoints.subRoutes.CredentialEndPoint;
import org.nms.endPoints.subRoutes.DiscoveryEndPoint;
import org.nms.endPoints.subRoutes.PollingEndPoint;
import org.nms.endPoints.subRoutes.ProvisionEndPoint;
import org.nms.utils.ConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Server extends AbstractVerticle
{
    private static final Logger LOGGER = LoggerFactory.getLogger(Server.class);
    private static final int PORT = ConfigLoader.get().getInteger("server.port");

    private static final String CREDENTIAL_PATH = "/api/v1/credential";
    private static final String DISCOVERY_PATH = "/api/v1/discovery";
    private static final String POLLING_PATH = "/api/v1/polling";
    private static final String PROVISION_PATH = "/api/v1/provision";

    @Override
    public void start(Promise<Void> startPromise)
    {
        var router = Router.router(vertx);

        // Mount REST sub-routes
        router.route(CREDENTIAL_PATH + "/*").subRouter(new CredentialEndPoint().createRouter(vertx));
        router.route(DISCOVERY_PATH + "/*").subRouter(new DiscoveryEndPoint().createRouter(vertx));
        router.route(POLLING_PATH + "/*").subRouter(new PollingEndPoint().createRouter(vertx));
        router.route(PROVISION_PATH + "/*").subRouter(new ProvisionEndPoint().createRouter(vertx));
        // Start HTTP server
        vertx.createHttpServer()
                .requestHandler(router)
                .listen(PORT, result ->
                {
                    if (result.succeeded())
                    {
                        LOGGER.info("HTTP server started on port {}", PORT);
                        startPromise.complete();
                    }
                    else
                    {
                        LOGGER.error("Failed to start HTTP server", result.cause());
                        startPromise.fail(result.cause());
                    }
                });
    }

    @Override
    public void stop(Promise<Void> stopPromise)
    {
        LOGGER.info("Stopping HTTP server...");
        vertx.close(stopPromise);
        LOGGER.info("HTTP server stopped.");
    }
}
