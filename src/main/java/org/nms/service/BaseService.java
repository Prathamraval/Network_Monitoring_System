package org.nms.service;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.nms.Main;
import org.nms.routerController.ApiResponse;
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
        this.vertx = Main.getVertx();
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
        var json =  entity;
        LOGGER.info("Creating entity: {}", json);
        try
        {
            var dbRequest = new JsonObject()
                    .put(Constants.DB_QUERY, getInsertQuery())
                    .put(Constants.DB_PARAMS, DbUtil.jsonToJsonArray(json, getJsonToParamsCreateMapping()));
            LOGGER.info("DB Request: {}", dbRequest);

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
                            LOGGER.error("Failed to insert entity");

                            promise.complete(ApiResponse.error(500, "Failed to insert entity").toJson());

                            return;
                        }

                        LOGGER.info("Entity inserted successfully");

                        var row = rows.getJsonArray("rows").getJsonObject(0);

                        var response = getResponseMapper().apply(json)
                                .put(getIdField(), row.getLong("id"));
                        LOGGER.info("Response: {}", response);
                        promise.complete(ApiResponse.success(response).toJson());
                    }
                    else
                    {
                        handleDbError(reply.cause(), promise);
                    }
                }
                catch (Exception exception)
                {
                    LOGGER.error("Error processing DB response: {}", exception.getMessage());

                    promise.complete(ApiResponse.error(500, "Error processing DB response").toJson());
                }
            });

            return promise.future();
        }
        catch (Exception exception)
        {
            LOGGER.error("Error handling create: {}", exception.getMessage());

            return Future.succeededFuture(ApiResponse.error(500, exception.getMessage()).toJson());
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

            vertx.eventBus().<JsonObject>request(Constants.DB_EXECUTE_WITHOUT_PARAM_EVENTBUS, dbRequest, reply ->
            {
                try
                {
                    if (reply.succeeded())
                    {
                        var rows = reply.result().body();

                        if (rows.getInteger("rowCount", 0) == 0)
                        {
                            LOGGER.error("No entities found");

                            promise.complete(ApiResponse.error(404, "No entities found").toJson());

                            return;
                        }

                        var entities = new JsonArray();
                        var rowsArray = rows.getJsonArray("rows", new JsonArray());
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

                        promise.complete(ApiResponse.success(new JsonObject().put("entities", entities)).toJson());
                    }
                    else
                    {
                        handleDbError(reply.cause(), promise);
                    }
                }
                catch (Exception exception)
                {
                    LOGGER.error("Error processing DB response: {}", exception.getMessage());

                    promise.complete(ApiResponse.error(500, "Error processing DB response").toJson());
                }
            });

            return promise.future();
        }
        catch (Exception exception)
        {
            LOGGER.error("Error handling fetch: {}", exception.getMessage());

            return Future.succeededFuture(ApiResponse.error(500, exception.getMessage()).toJson());
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

            vertx.eventBus().<JsonObject>request(Constants.DB_EXECUTE_PARAM_EVENTBUS, dbRequest, reply ->
            {
                try
                {
                    if (reply.succeeded())
                    {
                        var rows = reply.result().body();
                        if (rows.getInteger("rowCount", 0) == 0)
                        {
                            LOGGER.error("No entity found with ID: {}", id);
                            promise.complete(ApiResponse.error(404, "No entity found with ID: " + id).toJson());
                            return;
                        }

                        var row = rows.getJsonArray("rows").getJsonObject(0);
                        var response = getRowToResponseMapper().apply(row);
                        promise.complete(ApiResponse.success(new JsonObject().put("entity", response)).toJson());
                    }
                    else
                    {
                        handleDbError(reply.cause(), promise);
                    }
                }
                catch (Exception exception)
                {
                    LOGGER.error("Error processing DB response: {}", exception.getMessage());
                    promise.complete(ApiResponse.error(500, "Error processing DB response").toJson());
                }
            });

            return promise.future();
        }
        catch (Exception exception)
        {
            LOGGER.error("Error handling retrieval: {}", exception.getMessage());
            return Future.succeededFuture(ApiResponse.error(500, exception.getMessage()).toJson());
        }
    }

    public Future<JsonObject> update(JsonObject entity)
    {
        var json =  entity;
        LOGGER.info("Updating entity: {}", json);

        try
        {
            var id = json.getLong(getIdField());
            LOGGER.info("Entity ID: {}", id);
            if (id == null)
            {
                return Future.succeededFuture(ApiResponse.error(400, "ID is required").toJson());
            }

            var query = getUpdateQuery();
            var params = DbUtil.jsonToJsonArray(json, getJsonToParamsUpdateMapping());
            LOGGER.info("Params: {}", params);
            var dbRequest = new JsonObject()
                    .put(Constants.DB_QUERY, query)
                    .put(Constants.DB_PARAMS, params);

            var promise = Promise.<JsonObject>promise();

            vertx.eventBus().<JsonObject>request(Constants.DB_EXECUTE_PARAM_EVENTBUS, dbRequest, reply ->
            {
                try
                {
                    if (reply.succeeded())
                    {
                        LOGGER.info("Rows: {}", reply.result().body());
                        var rows = reply.result().body();

                        if (rows.getInteger("rowCount", 0) == 0)
                        {
                            LOGGER.error("Failed to update entity");
                            promise.complete(ApiResponse.error(500, "Failed to update entity").toJson());
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
                    promise.complete(ApiResponse.error(500, "Error processing DB response").toJson());
                }
            });

            return promise.future();
        }
        catch (Exception exception)
        {
            LOGGER.error("Error handling update: {}", exception.getMessage());
            return Future.succeededFuture(ApiResponse.error(500, exception.getMessage()).toJson());
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

            vertx.eventBus().<JsonObject>request(Constants.DB_EXECUTE_PARAM_EVENTBUS, dbRequest, reply ->
            {
                try
                {
                    if (reply.succeeded())
                    {
                        var rows = reply.result().body();
                        if (rows.getInteger("rowCount", 0) == 0)
                        {
                            LOGGER.error("No entity found with ID: {}", id);
                            promise.complete(ApiResponse.error(404, "No entity found with ID: " + id).toJson());
                            return;
                        }

                        LOGGER.info("Entity deleted successfully");
                        var response = new JsonObject().put("message", "Entity deleted successfully");
                        promise.complete(ApiResponse.success(response).toJson());
                    }
                    else
                    {
                        handleDbError(reply.cause(), promise);
                    }
                }
                catch (Exception exception)
                {
                    LOGGER.error("Error processing DB response: {}", exception.getMessage());
                    promise.complete(ApiResponse.error(500, "Error processing DB response").toJson());
                }
            });

            return promise.future();
        }
        catch (Exception exception)
        {
            LOGGER.error("Error handling deletion: {}", exception.getMessage());

            return Future.succeededFuture(ApiResponse.error(500, exception.getMessage()).toJson());
        }
    }

    public Future<JsonObject> customQueryExecutor(JsonObject dbRequest )
    {
        try
        {
            LOGGER.info("Executing custom query: {}", dbRequest);
            var promise = Promise.<JsonObject>promise();

            vertx.eventBus().<JsonObject>request(Constants.DB_EXECUTE_PARAM_EVENTBUS, dbRequest, reply ->
            {
                try
                {
                    if (reply.succeeded())
                    {
                        promise.complete(ApiResponse.success(new JsonObject().put("result",reply.result().body() )).toJson());
                    }
                    else
                    {
                        handleDbError(reply.cause(), promise);
                    }
                }
                catch (Exception exception)
                {
                    LOGGER.error("Error processing DB response: {}", exception.getMessage());
                    promise.complete(ApiResponse.error(500, "Error processing DB response").toJson());
                }
            });

            return promise.future();
        }
        catch (Exception exception)
        {
            LOGGER.error("Error handling retrieval: {}", exception.getMessage());
            return Future.succeededFuture(ApiResponse.error(500, exception.getMessage()).toJson());
        }
    }


    private void handleDbError(Throwable cause, Promise<JsonObject> promise)
    {
        LOGGER.error("Database operation failed: {}", cause.getMessage());
        var statusCode = 500;
        var errorMessage = cause.getMessage();

        if (errorMessage != null && errorMessage.contains("duplicate key value"))
        {
            statusCode = 409;
            errorMessage = "Entity with the same name already exists.";
            LOGGER.info(
                    "Duplicate key value error: {}. Status code: {}",
                    errorMessage, statusCode
            );
        }
        else if (errorMessage != null && errorMessage.contains("violates foreign key"))
        {
            statusCode = 400;
            errorMessage = "Foreign key Violation error.";
        }

        promise.complete(ApiResponse.error(statusCode, errorMessage).toJson());
    }
}