package org.nms.database.queries;

public class CredentialQueries
{

    // Create the credential_profiles table
    public static final String CREATE_CREDENTIAL_PROFILES_TABLE = """
            CREATE TABLE IF NOT EXISTS credential_profiles (
                id SERIAL PRIMARY KEY,
                profile_name VARCHAR(100) UNIQUE NOT NULL,
                protocol VARCHAR(50),
                username VARCHAR(50) NOT NULL,
                password VARCHAR(255)
            );""";

    // Create a new credential profile
    public static final String INSERT_CREDENTIAL_PROFILE = """
            INSERT INTO credential_profiles (profile_name, protocol, username, password)
            VALUES ($1, $2, $3, $4)
            RETURNING id""";

    // Read a credential profile by ID
    public static final String SELECT_CREDENTIAL_PROFILE_BY_ID = """
            SELECT id, profile_name, protocol, username, password
            FROM credential_profiles
            WHERE id = $1""";

    // Read a credential profile by profile_name
    public static final String SELECT_CREDENTIAL_PROFILE_BY_NAME = """
            SELECT id, profile_name, protocol, username, password
            FROM credential_profiles
            WHERE profile_name = $1""";

    // Read all credential profiles
    public static final String SELECT_ALL_CREDENTIAL_PROFILES = """
            SELECT id, profile_name, protocol, username, password
            FROM credential_profiles""";

    // Update a credential profile by ID
    public static final String UPDATE_CREDENTIAL_PROFILE = """
            UPDATE credential_profiles
            SET profile_name = $1, protocol = $2, username = $3, password = $4
            WHERE id = $5""";

    // Delete a credential profile by ID
    public static final String DELETE_CREDENTIAL_PROFILE = """
            DELETE FROM credential_profiles
            WHERE id = $1""";
}