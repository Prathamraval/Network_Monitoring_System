package org.nms.service;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.nms.Bootstrap;
import org.nms.endPoints.ApiResponse;
import org.nms.utils.Constants;
import org.nms.utils.DbUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

public abstract class BaseService<T>
{
    private static final Logger LOGGER = LoggerFactory.getLogger(BaseService.class);
    protected final Vertx vertx;

    public BaseService()
    {
        this.vertx = Bootstrap.getVertx();
    }

    protected abstract String getInsertQuery();
    protected abstract String getSelectAllQuery();
    protected abstract String getSelectByIdQuery();
    protected abstract String getUpdateQuery();
    protected abstract String getDeleteQuery();
    protected abstract String getIdField();
    protected abstract String[] getJsonToParamsCreateMapping();
    protected abstract String[] getJsonToParamsUpdateMapping();
    protected abstract Function<JsonObject, JsonObject> getResponseMapper();
    protected abstract Function<JsonObject, JsonObject> getRowToResponseMapper();

    public Future<JsonObject> create(JsonObject entity)
    {
        LOGGER.info("Creating entity: {}", entity);
        try
        {
            var dbRequest = new JsonObject()
                    .put(Constants.DB_QUERY, getInsertQuery())
                    .put(Constants.DB_PARAMS, DbUtil.jsonToJsonArray(entity, getJsonToParamsCreateMapping()));

            var promise = Promise.<JsonObject>promise();

            vertx.eventBus().<JsonObject>request(Constants.DB_EXECUTE_EVENTBUS, dbRequest, reply ->
            {
                try
                {
                    if (reply.succeeded())
                    {
                        var rows = reply.result().body();

                        if (rows.getInteger(Constants.ROW_COUNT, 0) == 0)
                        {
                            LOGGER.error("Failed to insert entity");
                            promise.fail("Failed to insert entity");
                            return;
                        }

                        LOGGER.info("Entity inserted successfully");

                        var row = rows.getJsonArray(Constants.ROWS).getJsonObject(0);

                        var response = getResponseMapper().apply(entity)
                                .put(Constants.SUCCESS,true)
                                .put(Constants.CRED_ID_RESPONSE, row.getLong(Constants.ID));

                        promise.complete(response);
                    }
                    else
                    {
                        handleDbError(reply.cause(), promise);
                    }
                }
                catch (Exception exception)
                {
                    LOGGER.error("Error processing DB response: {}", exception.getMessage());
                    promise.fail(exception);
                }
            });

            return promise.future();
        }
        catch (Exception exception)
        {
            LOGGER.error("Error handling create: {}", exception.getMessage());
            return Future.failedFuture(exception);
        }
    }

    public Future<JsonObject> getAll()
    {
        LOGGER.info("Fetching all entities");

        try
        {
            var dbRequest = new JsonObject()
                    .put(Constants.DB_QUERY, getSelectAllQuery());

            var promise = Promise.<JsonObject>promise();

            vertx.eventBus().<JsonObject>request(Constants.DB_EXECUTE_EVENTBUS, dbRequest, reply ->
            {
                try
                {
                    if (reply.succeeded())
                    {
                        var rows = reply.result().body();

                        if (rows.getInteger(Constants.ROW_COUNT, 0) == 0)
                        {
                            LOGGER.error("No entities found");
                            promise.fail("No entities found");
                            return;
                        }

                        var entities = new JsonArray();
                        var rowsArray = rows.getJsonArray(Constants.ROWS, new JsonArray());
                        for (var i = 0; i < rowsArray.size(); i++)
                        {
                            try
                            {
                                var row = rowsArray.getJsonObject(i);
                                entities.add(getRowToResponseMapper().apply(row));
                            }
                            catch (Exception exception)
                            {
                                LOGGER.error("Error processing row: {}", exception.getMessage());
                            }
                        }

                        LOGGER.info("Fetched {} entities", entities.size());
                        promise.complete(new JsonObject().put("entities", entities));
                    }
                    else
                    {
                        handleDbError(reply.cause(), promise);
                    }
                }
                catch (Exception exception)
                {
                    LOGGER.error("Error processing DB response: {}", exception.getMessage());
                    promise.fail(exception);
                }
            });

            return promise.future();
        }
        catch (Exception exception)
        {
            LOGGER.error("Error handling fetch: {}", exception.getMessage());
            return Future.failedFuture(exception);
        }
    }

    public Future<JsonObject> getById(Long id)
    {
        LOGGER.info("Fetching entity with ID: {}", id);

        try
        {
            var query = getSelectByIdQuery();
            var params = new JsonArray().add(id);

            var dbRequest = new JsonObject()
                    .put(Constants.DB_QUERY, query)
                    .put(Constants.DB_PARAMS, params);

            var promise = Promise.<JsonObject>promise();

            vertx.eventBus().<JsonObject>request(Constants.DB_EXECUTE_EVENTBUS, dbRequest, reply ->
            {
                try
                {
                    if (reply.succeeded())
                    {
                        var rows = reply.result().body();
                        if (rows.getInteger(Constants.ROW_COUNT, 0) == 0)
                        {
                            LOGGER.error("No entity found with ID: {}", id);
                            promise.fail("No entity found with ID: " + id);
                            return;
                        }

                        var row = rows.getJsonArray(Constants.ROWS).getJsonObject(0);
                        var response = getRowToResponseMapper().apply(row);
                        promise.complete(new JsonObject().put("entity", response));
                    }
                    else
                    {
                        handleDbError(reply.cause(), promise);
                    }
                }
                catch (Exception exception)
                {
                    LOGGER.error("Error processing DB response: {}", exception.getMessage());
                    promise.fail(exception);
                }
            });

            return promise.future();
        }
        catch (Exception exception)
        {
            LOGGER.error("Error handling retrieval: {}", exception.getMessage());
            return Future.failedFuture(exception);
        }
    }

    public Future<JsonObject> update(JsonObject entity)
    {
        LOGGER.info("Updating entity: {}", entity);

        try
        {
            var id = entity.getLong(getIdField());
            LOGGER.info("Entity ID: {}", id);
            if (id == null)
            {
                return Future.failedFuture("ID is required");
            }

            var query = getUpdateQuery();
            var params = DbUtil.jsonToJsonArray(entity, getJsonToParamsUpdateMapping());

            LOGGER.info("Params: {}", params);

            var dbRequest = new JsonObject()
                    .put(Constants.DB_QUERY, query)
                    .put(Constants.DB_PARAMS, params);

            var promise = Promise.<JsonObject>promise();

            vertx.eventBus().<JsonObject>request(Constants.DB_EXECUTE_EVENTBUS, dbRequest, reply ->
            {
                try
                {
                    if (reply.succeeded())
                    {
                        LOGGER.info("Rows: {}", reply.result().body());
                        var rows = reply.result().body();

                        if (rows.getInteger(Constants.ROW_COUNT, 0) == 0)
                        {
                            LOGGER.error("Failed to update entity");
                            promise.fail("Failed to update entity");
                            return;
                        }

                        LOGGER.info("Entity updated successfully");
                        promise.complete(ApiResponse.success("Entity updated successfully with ID: " + id).toJson());
                    }
                    else
                    {
                        handleDbError(reply.cause(), promise);
                    }
                }
                catch (Exception exception)
                {
                    LOGGER.error("Error processing DB response: {}", exception.getMessage());
                    promise.fail(exception);
                }
            });

            return promise.future();
        }
        catch (Exception exception)
        {
            LOGGER.error("Error handling update: {}", exception.getMessage());
            return Future.failedFuture(exception);
        }
    }

    public Future<JsonObject> delete(Long id)
    {
        LOGGER.info("Deleting entity with ID: {}", id);

        try
        {
            var query = getDeleteQuery();
            var params = new JsonArray().add(id);
            var dbRequest = new JsonObject()
                    .put(Constants.DB_QUERY, query)
                    .put(Constants.DB_PARAMS, params);
            var promise = Promise.<JsonObject>promise();

            vertx.eventBus().<JsonObject>request(Constants.DB_EXECUTE_EVENTBUS, dbRequest, reply ->
            {
                try
                {
                    if (reply.succeeded())
                    {
                        var rows = reply.result().body();
                        if (rows.getInteger(Constants.ROW_COUNT, 0) == 0)
                        {
                            LOGGER.error("No entity found with ID: {}", id);
                            promise.fail("No entity found with ID: " + id);
                            return;
                        }

                        LOGGER.info("Entity deleted successfully");
                        var response = new JsonObject().put("message", "Entity deleted successfully");
                        promise.complete(response);
                    }
                    else
                    {
                        handleDbError(reply.cause(), promise);
                    }
                }
                catch (Exception exception)
                {
                    LOGGER.error("Error processing DB response: {}", exception.getMessage());
                    promise.fail(exception);
                }
            });

            return promise.future();
        }
        catch (Exception exception)
        {
            LOGGER.error("Error handling deletion: {}", exception.getMessage());
            return Future.failedFuture(exception);
        }
    }

    public Future<JsonObject> customQueryExecutor(JsonObject dbRequest)
    {
        try
        {
            LOGGER.info("Executing custom query: {}", dbRequest);
            var promise = Promise.<JsonObject>promise();

            vertx.eventBus().<JsonObject>request(Constants.DB_EXECUTE_EVENTBUS, dbRequest, reply ->
            {
                try
                {
                    if (reply.succeeded())
                    {
                        promise.complete(new JsonObject().put("result", reply.result().body()));
                    }
                    else
                    {
                        handleDbError(reply.cause(), promise);
                    }
                }
                catch (Exception exception)
                {
                    LOGGER.error("Error processing DB response: {}", exception.getMessage());
                    promise.fail(exception);
                }
            });

            return promise.future();
        }
        catch (Exception exception)
        {
            LOGGER.error("Error handling retrieval: {}", exception.getMessage());
            return Future.failedFuture(exception);
        }
    }

    private void handleDbError(Throwable cause, Promise<JsonObject> promise)
    {
        LOGGER.error("Database operation failed: {}", cause.getMessage());
        var errorMessage = cause.getMessage();

        if (errorMessage != null && errorMessage.contains("duplicate key value"))
        {
            errorMessage = "Entity with the same name already exists.";
            LOGGER.info(
                    "Duplicate key value error: {}",
                    errorMessage
            );
            promise.fail(errorMessage);
        }
        else if (errorMessage != null && errorMessage.contains("violates foreign key"))
        {
            errorMessage = "Foreign key violation error.";
            promise.fail(errorMessage);
        }
        else
        {
            promise.fail(cause);
        }
    }
}