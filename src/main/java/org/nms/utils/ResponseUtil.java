package org.nms.utils;

import io.vertx.ext.web.RoutingContext;
import io.vertx.core.json.JsonObject;

public class ResponseUtil
{
    public static void handleResponse(RoutingContext ctx, JsonObject result)
    {
        var response = ctx.response();
        response.putHeader(Constants.CONTENT_TYPE, "application/json");

        var statusCode = result.getInteger(Constants.STATUS_CODE, 500);
        response.setStatusCode(statusCode);

        response.end(result.encodePrettily());
    }

}