package org.nms.routerController.subRoutes;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.nms.service.ProvisionService;
import org.nms.utils.Constants;
import org.nms.utils.MiddleWare;
import org.nms.utils.ResponseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProvisionRoutes extends BaseRoutes<JsonObject> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProvisionRoutes.class);

    public ProvisionRoutes() {
        super(new ProvisionService(), ProvisionService.PROVISION_ID);
    }

    @Override
    public Router createRouter(Vertx vertx) {
        Router router = super.createRouter(vertx);
        router.route().handler(BodyHandler.create());
        configureAdditionalRoutes(router);
        return router;
    }

    @Override
    protected void configureAdditionalRoutes(Router router) {
        // POST /:discoveryId for creating provision
        router.post("/:" + Constants.DISCOVERY_ID)
                .handler(context -> MiddleWare.validateContextPath(context, Constants.DISCOVERY_ID))
                .handler(context -> {
                    try {
                        var discoveryId = Long.parseLong(context.pathParam(Constants.DISCOVERY_ID));
                        ((ProvisionService) service).createProvision(discoveryId)
                                .onComplete(ar -> {
                                    if (ar.succeeded())
                                    {
                                        context.response()
                                                .setStatusCode(ar.result().getInteger("responseCode", 200))
                                                .putHeader("content-type", "application/json")
                                                .end(ar.result().encodePrettily());
                                    } else {
                                        ResponseUtil.handleResponse(context, new JsonObject().put("error",ar.result()));
                                    }
                                });
                    } catch (NumberFormatException e) {
                        MiddleWare.respondWithError(context, 400, "Invalid discoveryId format: " + e.getMessage());
                    }
                });

        // GET /status/:status for provisions by status
        router.get("/status/:" + Constants.PROVISION_STATUS)
                .handler(ctx -> {
                    try {
                        var status = Boolean.parseBoolean(ctx.pathParam(Constants.PROVISION_STATUS));
                        ((ProvisionService) service).getProvisionsByStatus(status)
                                .onComplete(ar -> {
                                    if (ar.succeeded()) {
                                        ctx.response()
                                                .setStatusCode(ar.result().getInteger("responseCode", 200))
                                                .putHeader("content-type", "application/json")
                                                .end(ar.result().encodePrettily());
                                    } else {
                                        MiddleWare.respondWithError(ctx, 500, "Failed to get provisions by status: " + ar.cause().getMessage());
                                    }
                                });
                    } catch (Exception e) {
                        MiddleWare.respondWithError(ctx, 400, "Invalid status format: " + e.getMessage());
                    }
                });
    }
}