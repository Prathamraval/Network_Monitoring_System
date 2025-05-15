package org.nms.routerController;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.ext.web.Router;
import org.nms.routerController.subRoutes.CredentialRoutes;
import org.nms.routerController.subRoutes.DiscoveryRoutes;
import org.nms.routerController.subRoutes.PollingRoutes;
import org.nms.routerController.subRoutes.ProvisionRoutes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpVerticle extends AbstractVerticle
{
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpVerticle.class);
    private static final int PORT = 8080;

    private static final String CREDENTIAL_PATH = "/api/v1/credential";
    private static final String DISCOVERY_PATH = "/api/v1/discovery";
    private static final String POLLING_PATH = "/api/v1/polling";
    private static final String PROVISION_PATH = "/api/v1/provision";


    @Override
    public void start(Promise<Void> startPromise)
    {
        var router = Router.router(vertx);

        // Mount sub-routers
        router.mountSubRouter(CREDENTIAL_PATH, new CredentialRoutes().createRouter(vertx));
        router.mountSubRouter(DISCOVERY_PATH, new DiscoveryRoutes().createRouter(vertx));
        router.mountSubRouter(POLLING_PATH, new PollingRoutes().createRouter(vertx));
        router.mountSubRouter(PROVISION_PATH, new ProvisionRoutes().createRouter(vertx));

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
                        startPromise.fail(result.cause());
                        LOGGER.error("Failed to start HTTP server: {}", result.cause().getMessage());
                    }
                });
    }
}