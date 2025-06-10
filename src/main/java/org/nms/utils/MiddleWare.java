package org.nms.utils;

import io.vertx.ext.web.RoutingContext;
import org.nms.endPoints.ApiResponse;
import org.nms.service.CredentialService;
import org.nms.service.DiscoveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class MiddleWare
{
    private static final Logger LOGGER = LoggerFactory.getLogger(MiddleWare.class);

    private static final String MESSAGE_REQUIRED_BODY = "Request body is required.";
    private static final String MESSAGE_INVALID_JSON = "Invalid JSON format.";
    private static final String MESSAGE_INVALID_IP = "Invalid IP address format.";
    private static final String MESSAGE_INVALID_FIELD = "Field '%s' cannot be null or empty.";
    private static final String MESSAGE_INVALID_PATH_PARAM_FORMAT = "ID must be a valid number.";

    private static final String IP_ADDRESS = Constants.DISC_IP_ADDRESS;

    public static void validateRequestBody(RoutingContext context)
    {
        var path = context.normalizedPath();
        var method = context.request().method().name();
        LOGGER.info("Validating request body for route: {} method: {}", path, method);

        try
        {
            List<String> requiredParams;
            if (path.startsWith("/api/v1/credential"))
            {
                requiredParams =Arrays.asList(CredentialService.CREATE_PARAM_MAPPING);
            }
            else if (path.startsWith("/api/v1/discovery"))
            {
                requiredParams = Arrays.asList(DiscoveryService.CREATE_PARAM_MAPPING);
            }
            else
            {
                ResponseUtil.handleResponse(context, ApiResponse.error(400, "Unknown API path.").toJson());
                return;
            }

            if (validateBody(context, requiredParams))
            {
                context.next();
            }
        }
        catch (Exception exception)
        {
            LOGGER.error("Validation error: {}", exception.getMessage());

            ResponseUtil.handleResponse(context, ApiResponse.error(400, MESSAGE_INVALID_JSON).toJson());

        }
    }

    public static boolean validateBody(RoutingContext context, List<String> requiredParams)
    {
        var body = context.getBodyAsJson();
        if (body == null || body.isEmpty())
        {
            ResponseUtil.handleResponse(context, ApiResponse.error(400, MESSAGE_REQUIRED_BODY).toJson());

            return false;
        }

        if (body.containsKey(IP_ADDRESS))
        {
            var ip = body.getString(IP_ADDRESS);
            if (!isValidIpAddress(ip))
            {
                ResponseUtil.handleResponse(context, ApiResponse.error(400, MESSAGE_INVALID_IP).toJson());

                return false;
            }
        }
        if(body.containsKey(Constants.DISC_WAIT_TIME))
        {
            var waitTime = body.getInteger(Constants.DISC_WAIT_TIME);
            if (waitTime == null || waitTime < 3 )
            {
                ResponseUtil.handleResponse(context, ApiResponse.error(400, "Wait time must be a non-negative integer and greater than or equals to 3.").toJson());

                return false;
            }
        }

        for (var param : requiredParams)
        {
            if (!body.containsKey(param) || body.getValue(param) == null
                    || (body.getValue(param) instanceof String && body.getString(param).trim().isEmpty()))
            {
                ResponseUtil.handleResponse(context, ApiResponse.error(400, String.format(MESSAGE_INVALID_FIELD, param)).toJson());

                return false;
            }
        }
        return true;
    }

    public static void validateContextPath(RoutingContext context, String id)
    {
        var idParam = context.pathParam(id);

        if (idParam == null || idParam.trim().isEmpty())
        {
            LOGGER.error("Missing or empty ID in path: {}", id);

            ResponseUtil.handleResponse(context, ApiResponse.error(400, "Missing or empty ID in path.").toJson());

            return;
        }

        try
        {
            Long.parseLong(idParam);
            context.next();
        }
        catch (NumberFormatException exception)
        {
            LOGGER.error("Invalid ID format in path: {}", idParam);

            ResponseUtil.handleResponse(context, ApiResponse.error(400, MESSAGE_INVALID_PATH_PARAM_FORMAT).toJson());

        }
    }

    private static boolean isValidIpAddress(String ip)
    {
        if (ip == null)
        {
            return false;
        }
        var ipRegex = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$";
        var pattern = Pattern.compile(ipRegex);
        return pattern.matcher(ip).matches();
    }

}