package org.nms.endPoints.subRoutes;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import org.nms.endPoints.ApiResponse;
import org.nms.service.DiscoveryService;
import org.nms.utils.Constants;
import org.nms.utils.ResponseUtil;

public class DiscoveryEndPoint extends BaseEndPoint<JsonObject>
{
    private static final String RUN_DISCOVERY_PATH = "/run/:discoveryId";
    private static final String DISCOVERY_STATUS_PATH = "/status/:status";
    private static final String DISCOVERY_STATUS = "status";
    private final DiscoveryService discoveryService;

    public DiscoveryEndPoint()
    {
        super(new DiscoveryService(), Constants.DISCOVERY_ID);
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
                        var discoveryId = Long.parseLong(ctx.pathParam(Constants.DISCOVERY_ID));
                        discoveryService.runDiscovery(discoveryId)
                                .onSuccess(result -> ResponseUtil.handleResponse(ctx, result))
                                .onFailure(error -> ResponseUtil.handleResponse(ctx, ApiResponse.error(400, error.getMessage()).toJson()));
                    }
                    catch (NumberFormatException exception)
                    {
                        ResponseUtil.handleResponse(ctx, ApiResponse.error(400, "Invalid discovery ID format").toJson());
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
                                .onFailure(error -> ResponseUtil.handleResponse(ctx, new JsonObject().put(Constants.ERROR, error.getMessage())));
                    }
                    catch (Exception exception)
                    {
                        ResponseUtil.handleResponse(ctx, ApiResponse.error(400, "Invalid status format").toJson());
                    }
                });
    }
}