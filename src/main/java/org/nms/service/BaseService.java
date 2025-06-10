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

    private Future<JsonObject> executeDbRequest(JsonObject dbRequest, String operation)
    {
        LOGGER.info("Executing {} operation with request: {}", operation, dbRequest);

        var promise = Promise.<JsonObject>promise();

        vertx.eventBus().<JsonObject>request(Constants.DB_EXECUTE_EVENTBUS, dbRequest, reply ->
        {
            try
            {
                if (reply.succeeded())
                {
                    var rows = reply.result().body();

                    promise.complete(rows);
                }
                else
                {
                    handleDbError(reply.cause(), promise);
                }
            }
            catch (Exception exception)
            {
                LOGGER.error("Error processing DB response for {}: {}", operation, exception.getMessage());
            }
        });

        return promise.future();
    }

    public Future<JsonObject> create(JsonObject entity)
    {
        LOGGER.info("Creating entity: {}", entity);

        try
        {
            var dbRequest = new JsonObject()
                    .put(Constants.DB_QUERY, getInsertQuery())
                    .put(Constants.DB_PARAMS, DbUtil.jsonToJsonArray(entity, getJsonToParamsCreateMapping()));

            return executeDbRequest(dbRequest, "create").compose(rows ->
            {
                if (rows.getInteger(Constants.ROW_COUNT, 0) == 0)
                {
                    LOGGER.error("Failed to insert entity");

                    return Future.failedFuture("Failed to insert entity");
                }

                LOGGER.info("Entity inserted successfully");

                var row = rows.getJsonArray(Constants.ROWS).getJsonObject(0);

                var response = getResponseMapper().apply(entity)
                        .put(Constants.ID, row.getLong(Constants.ID));

                return Future.succeededFuture(response);
            });
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

            return executeDbRequest(dbRequest, "getAll").compose(rows ->
            {
                if (rows.getInteger(Constants.ROW_COUNT, 0) == 0)
                {
                    LOGGER.error("No entities found");

                    return Future.failedFuture("No entities found");
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

                return Future.succeededFuture(new JsonObject().put("entities", entities));

            });
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

            return executeDbRequest(dbRequest, "getById").compose(rows ->
            {

                if (rows.getInteger(Constants.ROW_COUNT, 0) == 0)
                {

                    LOGGER.error("No entity found with ID: {}", id);

                    return Future.failedFuture("No entity found with ID: " + id);

                }

                var row = rows.getJsonArray(Constants.ROWS).getJsonObject(0);
                var response = getRowToResponseMapper().apply(row);

                return Future.succeededFuture(new JsonObject().put(Constants.ENTITY, response));
            });
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

            return executeDbRequest(dbRequest, "update").compose(rows ->
            {

                LOGGER.info("Rows: {}", rows);

                if (rows.getInteger(Constants.ROW_COUNT, 0) == 0)
                {
                    LOGGER.error("Failed to update entity");

                    return Future.failedFuture("Failed to update entity");
                }

                LOGGER.info("Entity updated successfully");
                return Future.succeededFuture(ApiResponse.success("Entity updated successfully with ID: " + id).toJson());

            });
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

            return executeDbRequest(dbRequest, "delete").compose(rows ->
            {
                if (rows.getInteger(Constants.ROW_COUNT, 0) == 0)
                {
                    LOGGER.error("No entity found with ID: {}", id);

                    return Future.failedFuture("No entity found with ID: " + id);
                }
                LOGGER.info("Entity deleted successfully");

                var response = new JsonObject().put("message", "Entity deleted successfully");

                return Future.succeededFuture(response);
            });
        }
        catch (Exception exception)
        {
            LOGGER.error("Error handling deletion: {}", exception.getMessage());

            return Future.failedFuture(exception);
        }
    }

    public Future<JsonObject> customQueryExecutor(JsonObject dbRequest)
    {
        LOGGER.info("Executing custom query: {}", dbRequest);
        try
        {
            return executeDbRequest(dbRequest, "customQuery").compose(rows ->
            {
                return Future.succeededFuture(new JsonObject().put("result", rows));
            });
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
            errorMessage = "Same entity already exist.";

            LOGGER.info("Duplicate key value error: {}", errorMessage);

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