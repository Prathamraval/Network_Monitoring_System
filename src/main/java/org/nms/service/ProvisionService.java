package org.nms.service;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.nms.database.queries.ProvisionQueries;
import org.nms.routerController.ApiResponse;
import org.nms.utils.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

public class ProvisionService extends BaseService<JsonObject> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProvisionService.class);

    public static final String PROVISION_ID = "monitorId";

    public static final String[] CREATE_PARAM_MAPPING = {
            Constants.DISCOVERY_ID
    };

    public static final String[] UPDATE_PARAM_MAPPING = {
            Constants.PROVISION_STATUS,
            Constants.MONITOR_ID
    };

    public ProvisionService() {
        super();
    }

    @Override
    protected String getInsertQuery() {
        return ProvisionQueries.INSERT_PROVISION;
    }

    @Override
    protected String getSelectAllQuery() {
        return ProvisionQueries.SELECT_ALL_PROVISIONS;
    }

    @Override
    protected String getSelectByIdQuery() {
        return ProvisionQueries.SELECT_PROVISION_BY_MONITOR_ID;
    }

    @Override
    protected String getUpdateQuery() {
        return ProvisionQueries.UPDATE_PROVISION_STATUS_BY_MONITOR_ID;
    }

    @Override
    protected String getDeleteQuery() {
        return ProvisionQueries.DELETE_PROVISION_BY_MONITOR_ID;
    }

    @Override
    protected String getIdField() {
        return PROVISION_ID;
    }

    @Override
    protected String[] getJsonToParamsCreateMapping() {
        return CREATE_PARAM_MAPPING;
    }

    @Override
    protected String[] getJsonToParamsUpdateMapping() {
        return UPDATE_PARAM_MAPPING;
    }


    @Override
    protected Function<JsonObject, JsonObject> getResponseMapper() {
        return json -> new JsonObject()
                .put("monitorId", json.getInteger(Constants.MONITOR_ID))
                .put("discoveryId", json.getLong(Constants.DISCOVERY_ID))
                .put("provisionStatus", json.getBoolean(Constants.PROVISION_STATUS));
    }

    @Override
    protected Function<JsonObject, JsonObject> getRowToResponseMapper() {
        return row -> new JsonObject()
                .put("monitorId", row.getInteger("monitor_id"))
                .put("discoveryId", row.getLong("discovery_id"))
                .put("provisionStatus", row.getBoolean("provision_status"))
                .put("discoveryName", row.getString("discoveryname"))
                .put("ipAddress", row.getString("ipaddress"))
                .put("portNo", row.getInteger("portno"))
                .put("lastDiscoveryTime", row.getString("lastdiscoverytime"));
    }

//    public Future<JsonObject> createProvision(Long discoveryId)
//    {
//        LOGGER.info("Creating provision for discoveryId: {}", discoveryId);
//
//        if (discoveryId == null)
//        {
//            return Future.succeededFuture(ApiResponse.error(400, "discoveryId is required").toJson());
//        }
//
//        return vertx.eventBus().<JsonObject>request(
//                Constants.DB_EXECUTE_PARAM_EVENTBUS,
//                new JsonObject()
//                        .put(Constants.DB_QUERY, ProvisionQueries.CHECK_DISCOVERY_ID_STATUS)
//                        .put(Constants.DB_PARAMS, new JsonArray().add(discoveryId))
//        ).compose(reply ->
//        {
//            var rows = reply.body();
//            if (rows.getInteger("rowCount", 0) == 0)
//            {
//                LOGGER.error("Discovery ID {} is not eligible for provisioning", discoveryId);
//                return Future.succeededFuture(ApiResponse.error(404, "No discovery profile found with the given ID").toJson());
//            }
//
//            var params = new JsonObject().put(Constants.DISCOVERY_ID, discoveryId);
//            return create(params);
//
//        }).compose(result -> {
//            if (result.getInteger("responseCode", 200) == 200) {
//                var data = result.getJsonObject("data");
//                var response = new JsonObject()
//                        .put(Constants.MONITOR_ID, data.getInteger("monitorId"))
//                        .put(Constants.DISCOVERY_ID, discoveryId)
//                        .put("message", "Provision successful");
//                return Future.succeededFuture(ApiResponse.success(response).toJson());
//            }
//            return Future.succeededFuture(result);
//        });
//    }

    public Future<JsonObject> createProvision(Long discoveryId)
    {
        try
        {
            if (discoveryId == null)
            {
                return Future.succeededFuture(ApiResponse.error(400, "discoveryId is required").toJson());
            }

            LOGGER.info("Creating provision for discoveryId: {}", discoveryId);

            // First, check if the discovery profile exists and is eligible for provisioning
            var query = ProvisionQueries.CHECK_DISCOVERY_ID_STATUS;
            var params = new JsonArray().add(discoveryId);

            // Create the request to send to DBVerticle
            var dbRequest = new JsonObject()
                    .put(Constants.DB_QUERY, query)
                    .put(Constants.DB_PARAMS, params);

            // Return a Future that will be completed when the DB operation is done
            var promise = Promise.<JsonObject>promise();

            vertx.eventBus().<JsonObject>request(Constants.DB_EXECUTE_PARAM_EVENTBUS, dbRequest, reply ->
            {
                try
                {
                    if (reply.succeeded())
                    {
                        var rows = reply.result().body();

                        if (rows.getInteger("rowCount", 0) == 0)
                        {

                            LOGGER.error("Discovery ID {} is not eligible for provisioning", discoveryId);

                            promise.complete(ApiResponse.error(404, "No discovery profile found with the given ID").toJson());

                            return;
                        }


                        // Step 2: Insert the provision record
                        var insertQuery = ProvisionQueries.INSERT_PROVISION;
                        var insertParams = new JsonArray().add(discoveryId);

                        var insertRequest = new JsonObject()
                                .put(Constants.DB_QUERY, insertQuery)
                                .put(Constants.DB_PARAMS, insertParams);

                        vertx.eventBus().<JsonObject>request(Constants.DB_EXECUTE_PARAM_EVENTBUS, insertRequest, insertReply ->
                        {
                            if (insertReply.succeeded())
                            {
                                var insertRows = insertReply.result().body();

                                if (insertRows.getInteger("rowCount", 0) == 0)
                                {
                                    LOGGER.error("Failed to insert provision record for discoveryId: {}", discoveryId);
                                    promise.complete(ApiResponse.error(500, "Failed to insert provision record").toJson());
                                    return;
                                }

                                LOGGER.info("Provision record inserted successfully for discoveryId: {}", discoveryId);

                                var insertRowsArray = insertRows.getJsonArray("rows", new JsonArray());
                                var insertRow = insertRowsArray.getJsonObject(0);
                                var monitorId = insertRow.getInteger("monitor_id");

                                var response = new JsonObject()
                                        .put(Constants.MONITOR_ID, monitorId)
                                        .put(Constants.DISCOVERY_ID, discoveryId)
                                        .put("message","Provision successful");

                                promise.complete(ApiResponse.success(response).toJson());
                            }
                            else
                            {
                                LOGGER.error("Failed to insert provision record: {}", insertReply.cause().getMessage());

                                var statusCode = 500;
                                var errorMessage = insertReply.cause().getMessage();

                                if (errorMessage != null && errorMessage.contains("foreign key constraint"))
                                {
                                    statusCode = 400;
                                    errorMessage = "Discovery ID does not exist in discovery_profiles";
                                }
                                else if (errorMessage != null && errorMessage.contains("duplicate key value"))
                                {
                                    statusCode = 409;
                                    errorMessage = "Device is already provisioned.";
                                }

                                promise.complete(ApiResponse.error(statusCode, errorMessage).toJson());
                            }
                        });
                    }
                    else
                    {
                        LOGGER.error("Failed to check eligibility for discoveryId {}: {}", discoveryId, reply.cause().getMessage());

                        promise.complete(ApiResponse.error(500, "Failed to check eligibility: " + reply.cause().getMessage()).toJson());
                    }
                }
                catch (Exception exception)
                {
                    LOGGER.error("Error in createProvision: {}", exception.getMessage());
                    promise.complete(ApiResponse.error(500, exception.getMessage()).toJson());
                }
            });

            return promise.future();
        }
        catch (Exception exception)
        {
            LOGGER.error("Error handling provision creation: {}", exception.getMessage());
            return Future.succeededFuture(ApiResponse.error(500, exception.getMessage()).toJson());
        }
    }

    public Future<JsonObject> getProvisionsByStatus(boolean status) {
        LOGGER.info("Fetching provisions with status: {}", status);

        return vertx.eventBus().<JsonObject>request(
                Constants.DB_EXECUTE_PARAM_EVENTBUS,
                new JsonObject()
                        .put(Constants.DB_QUERY, ProvisionQueries.SELECT_PROVISION_BY_STATUS)
                        .put(Constants.DB_PARAMS, new JsonArray().add(status))
        ).compose(reply -> {
            var rows = reply.body();
            var provisionList = new JsonArray();

            var rowsArray = rows.getJsonArray("rows", new JsonArray());
            for (int i = 0; i < rowsArray.size(); i++) {
                var row = rowsArray.getJsonObject(i);
                var provision = new JsonObject()
                        .put("monitorId", row.getInteger("monitor_id"))
                        .put("discoveryId", row.getLong("discovery_id"))
                        .put("provisionStatus", row.getBoolean("provision_status"));
                provisionList.add(provision);
            }

            return Future.succeededFuture(
                    ApiResponse.success(new JsonObject().put("provisions", provisionList)).toJson()
            );

        }).recover(err -> {
            LOGGER.error("Failed to get provisions by status: {}", err.getMessage());
            return Future.succeededFuture(
                    ApiResponse.error(500, "Failed to get provisions by status: " + err.getMessage()).toJson()
            );
        });

    }
}