package org.nms.routerController.subRoutes;

import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.nms.database.queries.ProvisionQueries;
import org.nms.routerController.ApiResponse;
import org.nms.service.ProvisionService;
import org.nms.utils.Constants;
import org.nms.utils.MiddleWare;
import org.nms.utils.ResponseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProvisionRoutes extends BaseRoutes<JsonObject>
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ProvisionRoutes.class);

    public ProvisionRoutes()
    {
        super(new ProvisionService(),Constants.MONITOR_ID);
    }

    @Override
    public Router createRouter(Vertx vertx)
    {
        Router router = super.createRouter(vertx);
        router.route().handler(BodyHandler.create());
        configureAdditionalRoutes(router);
        return router;
    }

    @Override
    protected void configureAdditionalRoutes(Router router)
    {
        // POST /:discoveryId for creating provision
        router.post("/:" + Constants.DISCOVERY_ID)
                .handler(context -> MiddleWare.validateContextPath(context, Constants.DISCOVERY_ID))
                .handler(context ->
                {
                    try
                    {
                        var discoveryId = Long.parseLong(context.pathParam(Constants.DISCOVERY_ID));
                        var request = new JsonObject()
                                .put(Constants.DB_QUERY, ProvisionQueries.SELECT_PROVISION_BY_DISCOVERY_ID)
                                .put(Constants.DB_PARAMS, new JsonArray().add(discoveryId));

                        ((ProvisionService) service)
                                .customQueryExecutor(request)
                                .compose(queryResult ->
                                {
                                    LOGGER.info("Query result: {}", queryResult);
                                    var result = queryResult.getJsonObject("data").getJsonObject("result");

                                    if (result.getInteger("rowCount")!=0)
                                    {
                                        if(result.getJsonArray("rows").getJsonObject(0).getBoolean("is_deleted"))
                                        {
                                            var softInsert = new JsonObject()
                                                    .put(Constants.DB_QUERY, ProvisionQueries.SOFT_INSERT_PROVISION)
                                                    .put(Constants.DB_PARAMS, new JsonArray().add(result.getJsonArray("rows").getJsonObject(0).getLong("monitor_id")));
                                             return ((ProvisionService) service).customQueryExecutor(softInsert);
                                        }
                                        else
                                        {
                                            LOGGER.info("Provision already exists for discoveryId: {}", discoveryId);
                                            var alreadyExistsPromise = Promise.<JsonObject>promise();
                                            alreadyExistsPromise.complete(ApiResponse.error(409,"Device is already provisioned").toJson());
                                            return alreadyExistsPromise.future();
                                        }
                                    }
                                    else
                                    {
                                        return ((ProvisionService) service).createProvision(discoveryId);
                                    }
                                })
                                .onComplete(result ->
                                {

                                    if (result.succeeded())
                                    {
                                        context.response()
                                                .setStatusCode(result.result().getInteger("responseCode", 200))
                                                .putHeader("content-type", "application/json")
                                                .end(result.result().encodePrettily());
                                    }
                                    else
                                    {
                                        ResponseUtil.handleResponse(context, new JsonObject().put("error", result.result()));
                                    }
                                });
                    }
                    catch (NumberFormatException exception)
                    {
                        MiddleWare.respondWithError(context, 400, "Invalid discoveryId format: " + exception.getMessage());
                    }
                });


        // GET /status/:status for provisions by status
        router.get("/status/:" + Constants.PROVISION_STATUS)
                .handler(context ->
                {
                    try
                    {
                        var status = Boolean.parseBoolean(context.pathParam(Constants.PROVISION_STATUS));
                        ((ProvisionService) service).getProvisionsByStatus(status)
                                .onComplete(result ->
                                {
                                    if (result.succeeded())
                                    {
                                        context.response()
                                                .setStatusCode(result.result().getInteger("responseCode", 200))
                                                .putHeader("content-type", "application/json")
                                                .end(result.result().encodePrettily());
                                    }
                                    else
                                    {
                                        MiddleWare.respondWithError(context, 500, "Failed to get provisions by status: " + result.cause().getMessage());
                                    }
                                });
                    }
                    catch (Exception exception)
                    {
                        MiddleWare.respondWithError(context, 400, "Invalid status format: " + exception.getMessage());
                    }
                });

        // PUT /:monitorId for updating provision
        router.put("/:"+Constants.PROVISION_STATUS+"/:" + Constants.MONITOR_ID)
                .handler(context -> MiddleWare.validateContextPath(context, Constants.MONITOR_ID))
                .handler(context ->
                {
                    try
                    {
                        var status = Boolean.parseBoolean(context.pathParam(Constants.PROVISION_STATUS));
                        var monitorId = Long.parseLong(context.pathParam(Constants.MONITOR_ID));

                        var updateProvision = new JsonObject()
                                .put(Constants.PROVISION_STATUS,status)
                                .put(Constants.MONITOR_ID,monitorId);

                        ((ProvisionService)service).update(updateProvision)
                                .onComplete(result ->
                                {
                                    if (result.succeeded())
                                    {
                                        LOGGER.info("Provision updated successfully for monitorId: {}", monitorId);

                                        context.response()
                                                .setStatusCode(result.result().getInteger("responseCode", 200))
                                                .putHeader("content-type", "application/json")
//                                                .end(ApiResponse.success("Provision updated successfully").toJson().encodePrettily());
                                                .end(result.result().encodePrettily());
                                    }
                                    else
                                    {
                                        MiddleWare.respondWithError(context, 500, "Failed to update provision: " + result.cause().getMessage());
                                    }
                                });
                    }
                    catch (NumberFormatException exception)
                    {
                        MiddleWare.respondWithError(context, 400, "Invalid format of path parameter: " + exception.getMessage());
                    }
                });
    }
}