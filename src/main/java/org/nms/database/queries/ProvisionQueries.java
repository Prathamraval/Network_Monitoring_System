package org.nms.database.queries;

public class ProvisionQueries
{
    // Create the provision table
    public static final String CREATE_PROVISION_TABLE = """
            CREATE TABLE IF NOT EXISTS provision (
                monitor_id SERIAL PRIMARY KEY,
                discovery_id INTEGER UNIQUE NOT NULL,
                status BOOLEAN DEFAULT TRUE,
                FOREIGN KEY (discovery_id) REFERENCES discovery_profiles(id)
            );

            """;
//            CREATE INDEX idx_provision_discovery_id ON provision(discovery_id);
//            CREATE INDEX idx_provision_monitor_id ON provision(monitor_id);

    // Insert a new provision record
    public static final String INSERT_PROVISION = """
            INSERT INTO provision (discovery_id)
            VALUES ($1)
            RETURNING monitor_id;
            """;

    // Select all provision records with discovery profile details
    public static final String SELECT_ALL_PROVISIONS = """
            SELECT 
                p.monitor_id,
                p.discovery_id,
                p.status AS provision_status,
                d.discovery_name,
                d.ip_address,
                d.portno,
                d.lastdiscoverytime
            FROM provision p
            JOIN discovery_profiles d ON p.discovery_id = d.id;
            """;

    public static final String SELECT_ALL_STATUS_TRUE_PROVISIONS = """
            SELECT
                p.monitor_id,
                p.discovery_id,
                p.status AS provision_status,
                d.discovery_name,
                d.ip_address,
                d.port_no,
                c.username,
                c.password,
                c.protocol
            FROM provision p
            JOIN discovery_profiles d ON p.discovery_id = d.id
            JOIN credential_profiles c ON d.credential_id = c.id
            WHERE p.status = TRUE;
            """;


    // Select discovery IDs that are eligible for provisioning (status = true)
    public static final String SELECT_ALL_ELIGIBLE_DISCOVERY_IDS = """
            SELECT id AS discovery_id
            FROM discovery_profiles
            WHERE status = true;
            """;

    public static final String CHECK_DISCOVERY_ID_STATUS = """
            SELECT id AS discovery_id
            FROM discovery_profiles
            WHERE status = true AND id = $1;
            """;

    // Select provision records by discovery ID
    public static final String SELECT_PROVISION_BY_DISCOVERY_ID = """
            SELECT 
                monitor_id,
                discovery_id,
                status
            FROM provision
            WHERE discovery_id = $1;
            """;

    // Select provision records by monitor ID
    public static final String SELECT_PROVISION_BY_MONITOR_ID = """
            SELECT 
                monitor_id,
                discovery_id,
                status
            FROM provision
            WHERE monitor_id = $1;
            """;

    // Update provision status by discovery ID
    public static final String UPDATE_PROVISION_STATUS_BY_DISCOVERY_ID = """
            UPDATE provision
            SET status = $1
            WHERE discovery_id = $2;
            """;

    // Update provision status by monitor ID
    public static final String UPDATE_PROVISION_STATUS_BY_MONITOR_ID = """
            UPDATE provision
            SET status = $1
            WHERE monitor_id = $2;
            """;

    // Delete provision record by discovery ID
    public static final String DELETE_PROVISION_BY_DISCOVERY_ID = """
            DELETE FROM provision
            WHERE discovery_id = $1;
            """;

    // Delete provision record by monitor ID
    public static final String DELETE_PROVISION_BY_MONITOR_ID = """
            DELETE FROM provision
            WHERE monitor_id = $1;
            """;

    public static final String SELECT_PROVISION_BY_STATUS= """
            SELECT 
                monitor_id,
                discovery_id,
                status
            FROM provision
            WHERE status = $1;
            """;
}