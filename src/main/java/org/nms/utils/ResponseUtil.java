package org.nms.utils;

import io.vertx.ext.web.RoutingContext;
import io.vertx.core.json.JsonObject;

public class ResponseUtil
{
    public static void handleResponse(RoutingContext ctx, JsonObject result)
    {
        var response = ctx.response();
        response.putHeader("content-type", "application/json");

        var statusCode = result.getInteger("statusCode", 500);
        response.setStatusCode(statusCode);

        response.end(result.encodePrettily());
    }
}