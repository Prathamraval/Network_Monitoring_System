package org.nms.endPoints.subRoutes;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.nms.endPoints.ApiResponse;
import org.nms.service.BaseService;
import org.nms.utils.Constants;
import org.nms.utils.MiddleWare;
import org.nms.utils.ResponseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseEndPoint<T>
{
    protected final BaseService<T> service;
    protected final String idPath;
    protected final String idField;

    public BaseEndPoint(BaseService<T> service, String idField)
    {
        this.service = service;
        this.idField = idField;
        this.idPath = "/:" + idField;
    }

    public Router createRouter(Vertx vertx)
    {
        var router = Router.router(vertx);
        router.route().handler(BodyHandler.create());

        router.post("/")
                .handler(MiddleWare::validateRequestBody)
                .handler(context ->
                {
                    service.create(context.getBodyAsJson())
                            .onSuccess(result ->
                            {
                                ResponseUtil.handleResponse(context, ApiResponse.success(result).toJson());
                            })
                            .onFailure(error ->
                            {
                                var statusCode = 500;
                                var message = error.getMessage();

                                if (message.equals("Failed to insert entity"))
                                {
                                    statusCode = 500;
                                }
                                else if (message.equals("Entity with the same name already exists."))
                                {
                                    statusCode = 409;
                                }
                                else if (message.equals("Foreign key violation error."))
                                {
                                    statusCode = 400;
                                    ResponseUtil.handleResponse(context, ApiResponse.error(statusCode, "Foreign key doesn't exist.").toJson());
                                }
                                ResponseUtil.handleResponse(context, ApiResponse.error(statusCode, message).toJson());
                            });
                });

        router.get("/")
                .handler(ctx ->
                {

                    service.getAll()
                            .onSuccess(result ->
                            {
                                ResponseUtil.handleResponse(ctx, ApiResponse.success(result).toJson());
                            })
                            .onFailure(error ->
                            {
                                var statusCode = 500;
                                var message = error.getMessage();

                                if (message.equals("No entities found"))
                                {
                                    statusCode = 404;
                                }
                                ResponseUtil.handleResponse(ctx, ApiResponse.error(statusCode, message).toJson());
                            });
                });

        router.get(idPath)
                .handler(ctx -> MiddleWare.validateContextPath(ctx, idField))
                .handler(ctx ->
                {
                    try
                    {
                        var id = Long.parseLong(ctx.pathParam(idField));
                        service.getById(id)
                                .onSuccess(result ->
                                {
                                    ResponseUtil.handleResponse(ctx, ApiResponse.success(result).toJson());
                                })
                                .onFailure(error ->
                                {
                                    var statusCode = 500;
                                    var message = error.getMessage();
                                    if (message.startsWith("No entity found with ID: "))
                                    {
                                        statusCode = 404;
                                    }
                                    ResponseUtil.handleResponse(ctx, ApiResponse.error(statusCode, message).toJson());
                                });
                    }
                    catch (NumberFormatException exception)
                    {
                        ResponseUtil.handleResponse(ctx, new JsonObject().put(Constants.ERROR, "Invalid ID format")
                                .put(Constants.STATUS_CODE, 400));
                    }
                });

        router.put(idPath)
                .handler(ctx -> MiddleWare.validateContextPath(ctx, idField))
                .handler(MiddleWare::validateRequestBody)
                .handler(ctx ->
                {
                    try
                    {
                        var id = Long.parseLong(ctx.pathParam(idField));
                        var requestBody = ctx.getBodyAsJson().put(idField, id);
                        service.update(requestBody)
                                .onSuccess(result ->
                                {
                                    ResponseUtil.handleResponse(ctx, ApiResponse.success(result).toJson());
                                })
                                .onFailure(error ->
                                {
                                    var statusCode = 500;
                                    var message = error.getMessage();

                                    if (message.equals("Failed to update entity"))
                                    {
                                        statusCode = 500;
                                    }
                                    else if (message.equals("ID is required"))
                                    {
                                        statusCode = 400;
                                    }
                                    else if (message.equals("Entity with the same name already exists."))
                                    {
                                        statusCode = 409;
                                    }
                                    else if (message.equals("Foreign key violation error."))
                                    {
                                        statusCode = 400;
                                        ResponseUtil.handleResponse(ctx, ApiResponse.error(statusCode, "Foreign key does not exist.").toJson());
                                    }
                                    ResponseUtil.handleResponse(ctx, ApiResponse.error(statusCode, message).toJson());
                                });
                    }
                    catch (NumberFormatException exception)
                    {
                        ResponseUtil.handleResponse(ctx, new JsonObject().put(Constants.ERROR, "Invalid ID format")
                                .put(Constants.STATUS_CODE, 400));
                    }
                });

        router.delete(idPath)
                .handler(ctx -> MiddleWare.validateContextPath(ctx, idField))
                .handler(ctx ->
                {
                    try
                    {
                        var id = Long.parseLong(ctx.pathParam(idField));
                        service.delete(id)
                                .onSuccess(result ->
                                {
                                    ResponseUtil.handleResponse(ctx, ApiResponse.success(result).toJson());
                                })
                                .onFailure(error ->
                                {
                                    var statusCode = 500;
                                    var message = error.getMessage();
                                    if (message.startsWith("No entity found with ID: "))
                                    {
                                        statusCode = 404;
                                    }
                                    else if (message.equals("Foreign key violation error."))
                                    {
                                        statusCode = 400;
                                        ResponseUtil.handleResponse(ctx, ApiResponse.error(statusCode, "Entity is already in use in other table .").toJson());
                                    }
                                        ResponseUtil.handleResponse(ctx, ApiResponse.error(statusCode, message).toJson());

                                });
                    }
                    catch (NumberFormatException exception)
                    {
                        ResponseUtil.handleResponse(ctx, new JsonObject().put(Constants.ERROR, "Invalid ID format")
                                .put(Constants.STATUS_CODE, 400));

                    }
                });

        configureAdditionalRoutes(router);

        return router;
    }

    protected abstract void configureAdditionalRoutes(Router router);
}