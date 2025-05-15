package org.nms.routerController.subRoutes;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import org.nms.service.BaseService;
import org.nms.service.PollingService;

public class PollingRoutes extends BaseRoutes<JsonObject>
{

    private final PollingService pollingService;

    public PollingRoutes()
    {
        super(new PollingService(), "pollingId");
        this.pollingService = (PollingService) service;
    }

    @Override
    protected void configureAdditionalRoutes(Router router)
    {

    }
}
