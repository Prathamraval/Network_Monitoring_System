package org.nms.endPoints.subRoutes;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import org.nms.service.PollingService;
import org.nms.utils.Constants;

public class PollingEndPoint extends BaseEndPoint<JsonObject>
{


    public PollingEndPoint()
    {
        super(new PollingService(), Constants.MONITOR_ID);
    }

    @Override
    protected void configureAdditionalRoutes(Router router)
    {

    }
}
