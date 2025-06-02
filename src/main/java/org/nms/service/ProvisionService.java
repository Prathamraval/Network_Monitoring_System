package org.nms.service;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.nms.database.queries.ProvisionQueries;
import org.nms.endPoints.ApiResponse;
import org.nms.utils.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

public class ProvisionService extends BaseService<JsonObject>
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ProvisionService.class);

    private static final String PROVISION ="provision" ;


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
                .put(Constants.MONITOR_ID, row.getInteger(Constants.MONITOR_ID))
                .put(Constants.DISC_IP_ADDRESS, row.getString(Constants.DISC_IP_ADDRESS))
                .put(Constants.DISC_PORT_NO, row.getInteger(Constants.DISC_PORT_NO))
                .put(Constants.CRED_USERNAME, row.getString(Constants.CRED_USERNAME))
                .put(Constants.CRED_PASSWORD, row.getString(Constants.CRED_PASSWORD))
                .put(Constants.CRED_PROTOCOL, row.getString(Constants.CRED_PROTOCOL))
                .put(Constants.STATUS, row.getBoolean(Constants.PROVISION_STATUS, true))
                .put(Constants.DISC_WAIT_TIME, row.getInteger(Constants.DISC_WAIT_TIME));
    }

    public Future<JsonObject> createProvision(Long discoveryId)
    {
        if (discoveryId == null)
        {
            return Future.failedFuture("discoveryId is required");
        }

        var dbRequest = new JsonObject()
                .put(Constants.DB_QUERY, ProvisionQueries.CHECK_DISCOVERY_ID_STATUS)
                .put(Constants.DB_PARAMS, new JsonArray().add(discoveryId));

        var promise = Promise.<JsonObject>promise();

        vertx.eventBus().<JsonObject>request(Constants.DB_EXECUTE_EVENTBUS, dbRequest)
                .compose(reply ->
                {
                    var rows = reply.body();
                    if (rows.getInteger(Constants.ROW_COUNT, 0) == 0)
                    {
                        LOGGER.error("Discovery ID {} is not eligible for provisioning", discoveryId);

                        return Future.failedFuture( "Discovery ID is not eligible for provisioning");
                    }

                    return create(new JsonObject().put(Constants.DISC_ID, discoveryId)).compose(insertReply ->
                    {
                        LOGGER.info("insert reply {}", insertReply);

                        var monitorId = insertReply.getValue(Constants.ID);

                        return getById((Long) monitorId).compose(result ->
                        {
                            LOGGER.info("Result from getById: {}", result.encodePrettily());
                            try
                            {
                                vertx.eventBus().send(Constants.EVENT_PROVISION_CHANGED, new JsonObject()
                                        .put(Constants.ACTION, "create")
                                        .put(PROVISION, result.getJsonObject(Constants.ENTITY).put(Constants.LAST_POLL,null)));
                            }
                            catch (Exception exception)
                            {
                                LOGGER.error("Failed to publish event: {}", exception.getMessage());
                            }
                            return Future.succeededFuture(insertReply);
                        });
                    });
                })
                .onSuccess(result->
                {
                    promise.complete(new JsonObject().put(Constants.MESSAGE,"Provision Successfull for discoveryId " + discoveryId));
                })
                .onFailure(cause ->
                {
                    LOGGER.error("Failed to create provision for discoveryId {}: {}", discoveryId, cause.getMessage());

                    promise.fail(cause.getMessage());
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

            return vertx.eventBus().<JsonObject>request(Constants.DB_EXECUTE_EVENTBUS, dbRequest)
                    .compose(reply ->
                    {
                        var rows = reply.body();
                        var provisionList = new JsonArray();

                        var rowsArray = rows.getJsonArray(Constants.ROWS, new JsonArray());
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

            vertx.eventBus().send(Constants.EVENT_PROVISION_CHANGED, new JsonObject()
                    .put(Constants.ACTION, "update")
                    .put(PROVISION, provision));

            return Future.succeededFuture(result);
        });
    }

    @Override
    public Future<JsonObject> delete(Long id)
    {
        return getById(id).compose(result ->
        {
            var provision = result.getJsonObject("response", new JsonObject()).getJsonObject(Constants.ENTITY, new JsonObject());

            return super.delete(id).compose(deleteResult ->
            {
                LOGGER.info("Provision deleted, publishing event");

                vertx.eventBus().send(Constants.EVENT_PROVISION_CHANGED, new JsonObject()
                        .put(Constants.ACTION, "delete")
                        .put(PROVISION, provision));

                return Future.succeededFuture(deleteResult);
            });
        });
    }
}