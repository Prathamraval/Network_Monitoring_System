package org.nms.routerController.subRoutes;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.nms.service.BaseService;
import org.nms.utils.MiddleWare;
import org.nms.utils.ResponseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseRoutes<T>
{
    private static final Logger LOGGER = LoggerFactory.getLogger(BaseRoutes.class);
    protected final BaseService<T> service;
    protected final String idPath;
    protected final String idField;

    public BaseRoutes(BaseService<T> service, String idField)
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
                .handler(ctx ->
                {
                    service.create(ctx.getBodyAsJson())
                            .onSuccess(result -> ResponseUtil.handleResponse(ctx, result))
                            .onFailure(error -> ResponseUtil.handleResponse(ctx, new JsonObject().put("error", error.getMessage())));
                });

        router.get("/")
                .handler(ctx ->
                {
                    service.getAll()
                            .onSuccess(result ->
                                    ResponseUtil.handleResponse(ctx, result)
                            )
                            .onFailure(error ->
                                    ResponseUtil.handleResponse(ctx, new JsonObject().put("error", error.getMessage()))
                            );
                });

        router.get(idPath)
                .handler(ctx -> MiddleWare.validateContextPath(ctx, idField))
                .handler(ctx ->
                {
                    try
                    {
                        var id = Long.parseLong(ctx.pathParam(idField));
                        service.getById(id)
                                .onSuccess(result -> ResponseUtil.handleResponse(ctx, result))
                                .onFailure(error -> ResponseUtil.handleResponse(ctx, new JsonObject().put("error", error.getMessage())));
                    }
                    catch (NumberFormatException exception)
                    {
                        ctx.response()
                                .setStatusCode(400)
                                .putHeader("content-type", "application/json")
                                .end(new JsonObject().put("error", "Invalid ID format").encodePrettily());
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
                                .onSuccess(result -> ResponseUtil.handleResponse(ctx, result))
                                .onFailure(error -> ResponseUtil.handleResponse(ctx, new JsonObject().put("error", error.getMessage())));
                    }
                    catch (NumberFormatException exception)
                    {
                        ctx.response()
                                .setStatusCode(400)
                                .putHeader("content-type", "application/json")
                                .end(new JsonObject().put("error", "Invalid ID format").encodePrettily());
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
                                .onSuccess(result -> ResponseUtil.handleResponse(ctx, result))
                                .onFailure(error -> ResponseUtil.handleResponse(ctx, new JsonObject().put("error", error.getMessage())));
                    }
                    catch (NumberFormatException exception)
                    {
                        ctx.response()
                                .setStatusCode(400)
                                .putHeader("content-type", "application/json")
                                .end(new JsonObject().put("error", "Invalid ID format").encodePrettily());
                    }
                });

        configureAdditionalRoutes(router);

        return router;
    }

    protected abstract void configureAdditionalRoutes(Router router);
}