package org.nms.utils;


import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonObject;
import java.nio.file.Files;
import java.nio.file.Paths;


public class ConfigLoader
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigLoader.class);

    private static JsonObject config;

    /**
     * Initializes the configuration by reading a JSON file from the specified path.
     * <p>
     * This method should be called once at application startup before any call to {@link #get()}.
     *
     * @param path the file system path to the JSON configuration file
     */
    public static void init(String path)
    {
        try
        {
            var content = Files.readString(Paths.get(path));

            config = new JsonObject(content);
        }
        catch (Exception exception)
        {
            LOGGER.error("Failed to load config file: " + exception.getMessage());
        }
    }

    /**
     * Retrieves the loaded configuration as a {@link JsonObject}.
     * <p>
     * This method returns the configuration that was loaded using the {@link #init(String)} method.
     * It should only be called after the configuration has been successfully initialized.
     *
     * @return the {@link JsonObject} containing the loaded configuration
     */
    public static JsonObject get()
    {
        return config;
    }
}