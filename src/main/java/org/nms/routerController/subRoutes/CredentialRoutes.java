package org.nms.routerController.subRoutes;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import org.nms.service.CredentialService;

public class CredentialRoutes extends BaseRoutes<JsonObject>
{
    public CredentialRoutes()
    {
        super(new CredentialService(), CredentialService.CREDENTIAL_ID);
    }

    @Override
    protected void configureAdditionalRoutes(Router router)
    {
        // No additional routes for CredentialRoutes
    }
}