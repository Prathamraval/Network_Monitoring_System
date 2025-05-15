package org.nms.database.queries;

public class DiscoveryQueries
{

    // Create the discovery_profiles table
    public static final String CREATE_DISCOVERY_PROFILES_TABLE = """
        CREATE TABLE IF NOT EXISTS discovery_profiles (
            id SERIAL PRIMARY KEY,
            discovery_name VARCHAR(100) UNIQUE NOT NULL,
            credential_id INTEGER NOT NULL,
            ip_address VARCHAR(50) NOT NULL,
            port_no INTEGER NOT NULL,
            status BOOLEAN DEFAULT FALSE,
            lastDiscoveryTime VARCHAR(50),
            message character varying(255),
            FOREIGN KEY (credential_id) REFERENCES credential_profiles(id) 
        );
    """;

    // Insert a new discovery profile
    public static final String INSERT_DISCOVERY_PROFILE = """
            INSERT INTO discovery_profiles (discovery_name, credential_id, ip_address, port_no)
            VALUES ($1, $2, $3, $4)
            RETURNING id""";

    // Read a discovery profile by ID
    public static final String SELECT_DISCOVERY_PROFILE_BY_ID = """
            SELECT id, discovery_name, credential_id, ip_address, port_no, status, lastDiscoveryTime, message
            FROM discovery_profiles
            WHERE id = $1""";

    public static final String SELECT_DISCOVERY_WITH_CREDENTIALS = """
        SELECT 
            d.id AS discovery_id,
            d.discovery_name,
            d.ip_address,
            d.status,
            d.port_no,
            d.lastdiscoverytime,
            c.id AS credential_id,
            c.profile_name,
            c.username,
            c.protocol
        FROM discovery_profiles d
        JOIN credential_profiles c ON d. credential_id = c.id;
    """;

    public static final String SELECT_DISCOVERY_BY_STATUS_WITH_CREDENTIALS = """
        SELECT 
            d.id AS discovery_id,
            d.discovery_name,
            d.ip_address,
            d.status,
            d.port_no,
            d.lastdiscoverytime,
            c.id AS credential_id,
            c.profile_name,
            c.username,
            c.protocol
        FROM discovery_profiles d
        JOIN credential_profiles c ON d.credential_id = c.id
        WHERE d.status = $1;
    """;

    public static final String SELECT_DISCOVERY_BY_ID_WITH_CREDENTIALS = """
    SELECT 
        d.id AS discovery_id,
        d.discovery_name,
        d.ip_address,
        d.status,
        d.port_no,
        d.lastdiscoverytime,
        c.id AS credential_id,
        c.profile_name,
        c.username,
        c.password,
        c.protocol
    FROM discovery_profiles d
    JOIN credential_profiles c ON d.credential_id = c.id
    WHERE d.id = $1;
""";

    // Read all discovery profiles
    public static final String SELECT_ALL_DISCOVERY_PROFILES = """
            SELECT id, discovery_name, credential_id, ip_address, port_no, status, lastDiscoveryTime, message
            FROM discovery_profiles""";

    // Update a discovery profile by ID
    public static final String UPDATE_DISCOVERY_PROFILE = """
            UPDATE discovery_profiles
            SET discovery_name = $1, credential_id = $2, ip_address = $3, port_no = $4, status = $5, lastDiscoveryTime = $6 , message = $7
            WHERE id = $8""";

    // Delete a discovery profile by ID
    public static final String DELETE_DISCOVERY_PROFILE = """
            DELETE FROM discovery_profiles
            WHERE id = $1""";

    // Read discovery profiles by status
    public static final String SELECT_DISCOVERY_PROFILES_BY_STATUS = """
        SELECT id, discovery_name, credential_id, ip_address, port_no, status, lastDiscoveryTime, message
        FROM discovery_profiles
        WHERE status = $1""";
}
