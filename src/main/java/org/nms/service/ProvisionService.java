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

public class ProvisionService extends BaseService<JsonObject>
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ProvisionService.class);

    private static final String EVENT_PROVISION_CHANGED = "provision.changed";

    public static final String[] CREATE_PARAM_MAPPING = {
            Constants.DISC_ID
    };

    public static final String[] UPDATE_PARAM_MAPPING = {
            Constants.PROVISION_STATUS,
            Constants.MONITOR_ID
    };

    public ProvisionService()
    {
        super();
    }

    @Override
    protected String getInsertQuery()
    {
        return ProvisionQueries.INSERT_PROVISION;
    }

    @Override
    protected String getSelectAllQuery()
    {
        return ProvisionQueries.SELECT_ALL_PROVISIONS;
    }

        @Override
    protected String getSelectByIdQuery()
    {
        return ProvisionQueries.SELECT_PROVISION_BY_MONITOR_ID;
    }

    @Override
    protected String getUpdateQuery()
    {
        return ProvisionQueries.UPDATE_PROVISION_STATUS_BY_ID;
    }

    @Override
    protected String getDeleteQuery()
    {
        return ProvisionQueries.DELETE_PROVISION_BY_MONITOR_ID;
    }

    @Override
    protected String getIdField()
    {
        return Constants.MONITOR_ID;
    }

    @Override
    protected String[] getJsonToParamsCreateMapping()
    {
        return CREATE_PARAM_MAPPING;
    }

    @Override
    protected String[] getJsonToParamsUpdateMapping()
    {
        return UPDATE_PARAM_MAPPING;
    }

    @Override
    protected Function<JsonObject, JsonObject> getResponseMapper()
    {
        return json -> new JsonObject();
    }

    @Override
    protected Function<JsonObject, JsonObject> getRowToResponseMapper()
    {
        return row -> new JsonObject()
                .put("monitor_id", row.getInteger(Constants.MONITOR_ID))
                .put("ip", row.getString(Constants.DISC_IP_ADDRESS))
                .put("port", row.getInteger(Constants.DISC_PORT_NO))
                .put("username", row.getString(Constants.CRED_USERNAME))
                .put("password", row.getString(Constants.CRED_PASSWORD))
                .put("protocol", row.getString(Constants.CRED_PROTOCOL))
                .put("status", row.getBoolean(Constants.PROVISION_STATUS, true));
    }

    public Future<JsonObject> createProvision(Long discoveryId)
    {
        if (discoveryId == null)
        {
            return Future.succeededFuture(ApiResponse.error(400, "discoveryId is required").toJson());
        }

        var dbRequest = new JsonObject()
                .put(Constants.DB_QUERY, ProvisionQueries.CHECK_DISCOVERY_ID_STATUS)
                .put(Constants.DB_PARAMS, new JsonArray().add(discoveryId));

        var promise = Promise.<JsonObject>promise();

        vertx.eventBus().<JsonObject>request(Constants.DB_EXECUTE_PARAM_EVENTBUS, dbRequest)
                .compose(reply ->
                {
                    var rows = reply.body();
                    if (rows.getInteger("rowCount", 0) == 0)
                    {
                        LOGGER.error("Discovery ID {} is not eligible for provisioning", discoveryId);

                        return Future.succeededFuture(ApiResponse.error(404, "No discovery profile found with the given ID").toJson());
                    }

                    return create(new JsonObject().put(Constants.DISC_ID, discoveryId)).compose(insertReply ->
                    {
                        if (!insertReply.getBoolean("success", true))
                        {
                            return Future.succeededFuture(insertReply);
                        }

                        var monitorId = insertReply.getJsonObject("data").getValue(Constants.MONITOR_ID);

                        return getById((Long) monitorId).compose(result ->
                        {
                            LOGGER.info("Result from getById: {}", result.encodePrettily());
                            try
                            {
                                vertx.eventBus().publish(EVENT_PROVISION_CHANGED, new JsonObject()
                                        .put("action", "create")
                                        .put("provision", result.getJsonObject("data").getJsonObject("entity")));
                            }
                            catch (Exception exception)
                            {
                                LOGGER.error("Failed to publish event: {}", exception.getMessage());
                            }
                            return Future.succeededFuture(insertReply);
                        });
                    });
                })
                .onSuccess(promise::complete)
                .onFailure(cause ->
                {
                    LOGGER.error("Failed to create provision for discoveryId {}: {}", discoveryId, cause.getMessage());

                    promise.complete(ApiResponse.error(500, "Failed to create provision: " + cause.getMessage()).toJson());
                });

        return promise.future();
    }

    public Future<JsonObject> getProvisionsByStatus(boolean status)
    {
        LOGGER.info("Fetching provisions with status: {}", status);
        try
        {
            var dbRequest = new JsonObject()
                    .put(Constants.DB_QUERY, ProvisionQueries.SELECT_PROVISIONS_BY_STATUS)
                    .put(Constants.DB_PARAMS, new JsonArray().add(status));

            return vertx.eventBus().<JsonObject>request(Constants.DB_EXECUTE_PARAM_EVENTBUS, dbRequest)
                    .compose(reply ->
                    {
                        var rows = reply.body();
                        var provisionList = new JsonArray();

                        var rowsArray = rows.getJsonArray("rows", new JsonArray());
                        for (var i = 0; i < rowsArray.size(); i++)
                        {
                            try
                            {
                                var row = rowsArray.getJsonObject(i);
                                var provision = new JsonObject()
                                        .put(Constants.MONITOR_ID, row.getLong(Constants.MONITOR_ID))
                                        .put(Constants.DISC_NAME, row.getString(Constants.DISC_NAME))
                                        .put(Constants.PROVISION_STATUS, row.getBoolean("status", true));

                                provisionList.add(provision);
                            }
                            catch (Exception exception)
                            {
                                LOGGER.error("Error processing row {}: {}", i, exception.getMessage());
                            }
                        }

                        return Future.succeededFuture(ApiResponse.success(new JsonObject().put("provisions", provisionList)).toJson());
                    })
                    .recover(error ->
                    {
                        LOGGER.error("Failed to fetch provisions: {}", error.getMessage());
                        return Future.succeededFuture(ApiResponse.error(500, "Failed to fetch provisions: " + error.getMessage()).toJson());
                    });
        }
        catch (Exception exception)
        {
            LOGGER.error("Error in getProvisionsByStatus: {}", exception.getMessage());
            return Future.succeededFuture(ApiResponse.error(500, "Error fetching provisions: " + exception.getMessage()).toJson());
        }
    }

    @Override
    public Future<JsonObject> update(JsonObject entity)
    {
        return super.update(entity).compose(result ->
        {
            LOGGER.info("Provision updated, publishing event");
            var provision = entity; // Use input entity for simplicity
            vertx.eventBus().publish(EVENT_PROVISION_CHANGED, new JsonObject()
                    .put("action", "update")
                    .put("provision", provision));
            return Future.succeededFuture(result);
        });
    }

    @Override
    public Future<JsonObject> delete(Long id) {
        return getById(id).compose(result -> {
            JsonObject provision = result.getJsonObject("response", new JsonObject()).getJsonObject("entity", new JsonObject());
            return super.delete(id).compose(deleteResult -> {
                LOGGER.info("Provision deleted, publishing event");
                vertx.eventBus().publish(EVENT_PROVISION_CHANGED, new JsonObject()
                        .put("action", "delete")
                        .put("provision", provision));
                return Future.succeededFuture(deleteResult);
            });
        });
    }
}