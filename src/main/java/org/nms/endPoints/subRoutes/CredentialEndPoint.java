package org.nms.endPoints.subRoutes;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import org.nms.service.CredentialService;

public class CredentialEndPoint extends BaseEndPoint<JsonObject>
{
    public CredentialEndPoint()
    {
        super(new CredentialService(), CredentialService.CREDENTIAL_ID);
    }

    @Override
    protected void configureAdditionalRoutes(Router router)
    {
        // No additional routes for CredentialRoutes
    }
}