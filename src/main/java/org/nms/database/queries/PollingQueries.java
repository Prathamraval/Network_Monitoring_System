package org.nms.database.queries;

public class PollingQueries
{

    // Create the polling_data table
    public static final String CREATE_POLLING_DATA_TABLE = """
            
            CREATE TABLE IF NOT EXISTS Polling_data (
                monitor_id INTEGER NOT NULL,
                data JSONB NOT NULL,
                timestamp VARCHAR(50) DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (monitor_id) REFERENCES provision(monitor_id)
            );
           """;

//    -- Add index for faster lookups
//    CREATE INDEX IF NOT EXISTS idx_polling_data_monitor_id ON Polling_data(monitor_id);
//    CREATE INDEX IF NOT EXISTS idx_polling_data_timestamp ON Polling_data(timestamp);


    // Create a new polling profile
    public static final String INSERT_POLLING_DATA_PROFILE = """
            INSERT INTO Polling_data (monitor_id, data, timestamp) VALUES ($1, $2, $3)
            """;

    // Read a polling profile by ID
    public static final String SELECT_POLLING_PROFILE_BY_ID = """
            SELECT monitor_id, data, timestamp
            FROM Polling_data
            WHERE id = $1""";

    // Read all polling profiles
    public static final String SELECT_ALL_POLLING_DATA = """
            SELECT monitor_id, data, timestamp
            FROM Polling_data""";


    // Delete a polling profile by ID
    public static final String DELETE_POLLING_PROFILE = """
            DELETE FROM Polling_data
            WHERE monitor_id = $1""";

    public static final String UPDATE_POLLING_DATA_PROFILE = """
        UPDATE polling_data
        SET data = $1, timestamp = $2
        WHERE monitor_id = $3;
    """;
}
