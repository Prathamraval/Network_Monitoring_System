package org.nms.routerController.subRoutes;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import org.nms.routerController.ApiResponse;
import org.nms.service.DiscoveryService;
import org.nms.utils.ResponseUtil;

public class DiscoveryRoutes extends BaseRoutes<JsonObject>
{
    private static final String RUN_DISCOVERY_PATH = "/run/:discoveryId";
    private static final String DISCOVERY_STATUS_PATH = "/status/:status";
    private static final String DISCOVERY_STATUS = "status";
    private final DiscoveryService discoveryService;

    public DiscoveryRoutes()
    {
        super(new DiscoveryService(), "discoveryId");
        this.discoveryService = (DiscoveryService) service;
    }

    @Override
    protected void configureAdditionalRoutes(Router router)
    {
        router.post(RUN_DISCOVERY_PATH)
                .handler(ctx ->
                {
                    try
                    {
                        var discoveryId = Long.parseLong(ctx.pathParam("discoveryId"));
                        discoveryService.runDiscovery(discoveryId)
                                .onSuccess(result -> ResponseUtil.handleResponse(ctx, result))
                                .onFailure(error -> ResponseUtil.handleResponse(ctx, new JsonObject().put("error", error.getMessage())));
                    }
                    catch (NumberFormatException exception)
                    {
                        ctx.response()
                                .setStatusCode(400)
                                .putHeader("content-type", "application/json")
                                .end(new JsonObject().put("error", "Invalid discovery ID format").encodePrettily());
                    }
                });

        router.get(DISCOVERY_STATUS_PATH)
                .handler(ctx ->
                {
                    try
                    {
                        var status = Boolean.parseBoolean(ctx.pathParam(DISCOVERY_STATUS));
                        discoveryService.getDiscoveriesByStatus(status)
                                .onSuccess(result -> ResponseUtil.handleResponse(ctx, result))
                                .onFailure(error -> ResponseUtil.handleResponse(ctx, new JsonObject().put("error", error.getMessage())));
                    }
                    catch (Exception exception)
                    {
                        ResponseUtil.handleResponse(ctx, ApiResponse.error(400, "Invalid status format. Must be true or false").toJson());
                    }
                });
    }
}