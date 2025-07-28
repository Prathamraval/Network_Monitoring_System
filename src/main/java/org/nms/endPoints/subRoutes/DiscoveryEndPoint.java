package org.nms.endPoints.subRoutes;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import org.nms.database.queries.DiscoveryQueries;
import org.nms.endPoints.ApiResponse;
import org.nms.service.DiscoveryService;
import org.nms.utils.Constants;
import org.nms.utils.ResponseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DiscoveryEndPoint extends BaseEndPoint<JsonObject>
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DiscoveryEndPoint.class);
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
        router.post(RUN_DISCOVERY_PATH).handler(ctx ->
        {
            try
            {
                var discoveryId = Long.parseLong(ctx.pathParam(Constants.DISCOVERY_ID));

                ctx.response()
                        .putHeader(Constants.CONTENT_TYPE, "text/event-stream")
                        .putHeader("Access-Control-Allow-Origin", "*")
                        .setChunked(true);

                // Validate discoveryId and build discoveryDetails
                var dbRequest = new JsonObject()
                        .put(Constants.DB_QUERY, DiscoveryQueries.SELECT_DISCOVERY_BY_ID_WITH_CREDENTIALS)
                        .put(Constants.DB_PARAMS, new JsonArray().add(discoveryId));

                discoveryService.customQueryExecutor(dbRequest).onComplete(dbResult ->
                {
                    if (dbResult.failed())
                    {
                        LOGGER.error("Failed to fetch discovery profile: {}", dbResult.cause().getMessage());
                        ResponseUtil.handleResponse(ctx, ApiResponse.error(500, dbResult.cause().getMessage()).toJson());
                        return;
                    }

                    var rows = dbResult.result().getJsonObject(Constants.RESULT);

                    if (rows.getInteger(Constants.ROW_COUNT) == 0)
                    {
                        LOGGER.error("Discovery profile not found with ID: {}", discoveryId);
                        ResponseUtil.handleResponse(ctx, ApiResponse.error(404, "Discovery profile not found").toJson());
                        return;
                    }

                        // Send immediate acceptance message
                        var acceptanceMessage = new JsonObject()
                                .put(Constants.STATUS, "accepted")
                                .put(Constants.MESSAGE, "Discovery request accepted for ID: " + discoveryId + ". Processing...")
                                .put(Constants.DISC_ID, discoveryId);

                        ctx.response().write("data: " + acceptanceMessage.encode() + "\n\n");


                    // Build discoveryDetails
                    var row = rows.getJsonArray(Constants.ROWS).getJsonObject(0);

                    var discoveryDetails = buildDiscoveryDetails(row);

                    discoveryService.processRunDiscovery(discoveryDetails)
                            .onSuccess(result ->
                            {
                                if (!ctx.response().ended())
                                {
                                    ctx.response().write("data: " + result + "\n\n");
                                    ctx.response().end();
                                }
                            })
                            .onFailure(error ->
                            {
                                if (!ctx.response().ended()) {
                                    var errorMessage = new JsonObject()
                                            .put(Constants.STATUS, "error")
                                            .put(Constants.MESSAGE, error.getMessage());
                                    ctx.response().write("data: " + errorMessage.encode() + "\n\n");
                                    ctx.response().end();
                                }
                            });

                });
            }
            catch (Exception exception)
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

            router.get("/provisioned/:status")
                .handler(ctx ->
                {
                    try
                    {
                        var status = Boolean.parseBoolean(ctx.pathParam(Constants.STATUS));
                        discoveryService.getNotProvisionedDiscoveries(status)
                                .onSuccess(result -> ResponseUtil.handleResponse(ctx, result))
                                .onFailure(error -> ResponseUtil.handleResponse(ctx, new JsonObject().put(Constants.ERROR, error.getMessage())));
                    }
                    catch (Exception exception)
                    {
                        ResponseUtil.handleResponse(ctx, ApiResponse.error(400, "Invalid status format").toJson());
                    }

                });
    }

    public static JsonObject buildDiscoveryDetails(JsonObject row)
    {
        return new JsonObject()
                .put(Constants.DISCOVERY_ID, row.getLong(Constants.DISC_ID))
                .put(Constants.DISC_NAME, row.getString(Constants.DISC_NAME))
                .put(Constants.DISC_IP_ADDRESS, row.getString(Constants.DISC_IP_ADDRESS))
                .put(Constants.DISC_PORT_NO, row.getInteger(Constants.DISC_PORT_NO))
                .put(Constants.STATUS, row.getBoolean(Constants.STATUS))
                .put(Constants.DISC_CREDENTIAL_ID, row.getLong(Constants.DISC_CREDENTIAL_ID))
                .put(Constants.CRED_USERNAME, row.getValue(Constants.CRED_USERNAME))
                .put(Constants.CRED_PASSWORD, row.getValue(Constants.CRED_PASSWORD))
                .put(Constants.CRED_PROTOCOL, row.getValue(Constants.CRED_PROTOCOL))
                .put(Constants.DISC_WAIT_TIME, row.getInteger(Constants.DISC_WAIT_TIME));
    }
}