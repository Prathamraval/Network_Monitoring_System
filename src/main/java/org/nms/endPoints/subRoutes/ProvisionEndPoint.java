package org.nms.endPoints.subRoutes;

import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.nms.database.queries.ProvisionQueries;
import org.nms.endPoints.ApiResponse;
import org.nms.service.ProvisionService;
import org.nms.utils.Constants;
import org.nms.utils.MiddleWare;
import org.nms.utils.ResponseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProvisionEndPoint extends BaseEndPoint<JsonObject>
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ProvisionEndPoint.class);

    public ProvisionEndPoint()
    {
        super(new ProvisionService(),Constants.MONITOR_ID);
    }

    @Override
    public Router createRouter(Vertx vertx)
    {
        var router = super.createRouter(vertx);
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

                        (service)
                                .customQueryExecutor(request)
                                .compose(queryResult ->
                                {
                                    LOGGER.info("Query result: {}", queryResult);
                                    var result = queryResult.getJsonObject("result");

                                    if (result.getInteger(Constants.ROW_COUNT)!=0)
                                    {
                                        if(result.getJsonArray(Constants.ROWS).getJsonObject(0).getBoolean("is_deleted"))
                                        {
                                            var softInsert = new JsonObject()
                                                    .put(Constants.DB_QUERY, ProvisionQueries.SOFT_INSERT_PROVISION)
                                                    .put(Constants.DB_PARAMS, new JsonArray().add(result.getJsonArray(Constants.ROWS).getJsonObject(0).getLong("monitor_id")));

                                             return (service).customQueryExecutor(softInsert);
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
                                        ResponseUtil.handleResponse(context, ApiResponse.success(result.result()).toJson());
                                    }
                                    else
                                    {
                                        ResponseUtil.handleResponse(context, ApiResponse.error(500, "Failed to create provision: " + result.cause().getMessage()).toJson());
                                    }
                                });
                    }
                    catch (NumberFormatException exception)
                    {
                        ResponseUtil.handleResponse(context, ApiResponse.error(400, "Invalid discoveryId format:"+ exception.getMessage()).toJson());

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
                                      ResponseUtil.handleResponse(context, ApiResponse.success(result.result()).toJson());
                                    }
                                    else
                                    {
                                        ResponseUtil.handleResponse(context, ApiResponse.error(500, "Failed to get provisions by status:"+ result.cause().getMessage()).toJson());
                                    }
                                });
                    }
                    catch (Exception exception)
                    {
                        ResponseUtil.handleResponse(context, ApiResponse.error(400, "Invalid discoveryId format:"+ exception.getMessage()).toJson());
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
                        LOGGER.info("updateProvision: {}", updateProvision);
                        service.update(updateProvision)
                                .onComplete(result ->
                                {
                                    if (result.succeeded())
                                    {
                                        LOGGER.info("Provision updated successfully for monitorId: {}", monitorId);

                                        ResponseUtil.handleResponse(context, ApiResponse.success(result.result()).toJson());
                                    }
                                    else
                                    {
                                        ResponseUtil.handleResponse(context, ApiResponse.error(500, "Failed to update provision:"+ result.cause().getMessage()).toJson());
                                    }
                                });
                    }
                    catch (NumberFormatException exception)
                    {
                        ResponseUtil.handleResponse(context, ApiResponse.error(400, "Invalid format of path parameter"+exception.getMessage()).toJson());
                    }
                });
    }
}