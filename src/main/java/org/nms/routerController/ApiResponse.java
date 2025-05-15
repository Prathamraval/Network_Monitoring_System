package org.nms.routerController;

import io.vertx.core.json.JsonObject;

/**
 * Standardized API response format.
 * This class provides a consistent way to format responses across the application.
 */
public class ApiResponse
{
    private final int statusCode;
    private final boolean success;
    private final JsonObject data;
    private final String message;

    private ApiResponse(int statusCode, boolean success, JsonObject data, String message)
    {
        this.statusCode = statusCode;
        this.success = success;
        this.data = data != null ? data : new JsonObject();
        this.message = message;
    }

    /**
     * Create a success response with data
     * @param data The response data
     * @return ApiResponse instance
     */
    public static ApiResponse successMethod(JsonObject data)
    {
        return new ApiResponse(200, true, data, null);
    }

    /**
     * Create a success response with data and a message
     * @param data The response data
     * @param message Success message
     * @return ApiResponse instance
     */
    public static ApiResponse success(JsonObject data, String message)
    {
        return new ApiResponse(200, true, data, message);
    }

    /**
     * Create a success response with just a message
     * @param message Success message
     * @return ApiResponse instance
     */
    public static ApiResponse success(String message)
    {
        return new ApiResponse(200, true, null, message);
    }

    public static ApiResponse success(JsonObject data)
    {
        return new ApiResponse(200, true, data, null);
    }

    /**
     * Create an error response
     * @param statusCode HTTP status code
     * @param message Error message
     * @return ApiResponse instance
     */
    public static ApiResponse error(int statusCode, String message)
    {
        return new ApiResponse(statusCode, false, null, message);
    }

    /**
     * Create an error response with additional data
     * @param statusCode HTTP status code
     * @param message Error message
     * @param data Additional error data
     * @return ApiResponse instance
     */
    public static ApiResponse error(int statusCode, String message, JsonObject data)
    {
        return new ApiResponse(statusCode, false, data, message);
    }

    /**
     * Convert the response to a JsonObject
     * @return JsonObject representation of the response
     */
    public JsonObject toJson()
    {
        JsonObject json = new JsonObject()
                .put("success", success)
                .put("statusCode", statusCode);

        if (message != null && !message.isEmpty())
        {
            json.put("message", message);
        }

        if (data != null && !data.isEmpty())
        {
            json.put("data", data);
        }

        return json;
    }

    // Getters
    public int getStatusCode()
    {
        return statusCode;
    }

    public boolean isSuccess()
    {
        return success;
    }

    public JsonObject getData()
    {
        return data;
    }

    public String getMessage()
    {
        return message;
    }
}