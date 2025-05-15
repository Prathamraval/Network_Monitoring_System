//package org.nms.utils;
//
///**
// * Constants used throughout the application.
// * Centralizes configuration values and string constants to avoid hardcoding.
// */
//public final class Constants
//{
//    // Prevent instantiation
//    private Constants() {}
//
//    // Server configuration
//    public static final int SERVER_PORT = 8080;
//    public static final String SERVER_HOST = "0.0.0.0";
//
//    // Database configuration
//    public static final String DB_HOST = "localhost";
//    public static final int DB_PORT = 5432;
//    public static final String DB_NAME = "nms_new";
//    public static final String DB_USER = "postgres";
//    public static final String DB_PASSWORD = "moontomarsis#2";
//
//    // Connection pool settings
//    public static final int DB_POOL_MAX_SIZE = 10;
//    public static final int DB_POOL_IDLE_TIMEOUT = 1800; // 30 minutes
//
//    // Event bus addresses
//    public static final String EB_DB_EXECUTE = "db.execute";
//    public static final String EB_DB_EXECUTE_WITH_PARAMS = "db.execute.params";
//    public static final String EB_ZMQ_SEND = "zmq.send";
//    public static final String EB_ZMQ_RESPONSE = "zmq.response";
//    public static final String DB_QUERY = "query";
//    public static final String DB_PARAMS = "params";
//
//    // ZMQ configuration
//    public static final String ZMQ_PUSH_ENDPOINT = "tcp://localhost:5555";
//    public static final String ZMQ_PULL_ENDPOINT = "tcp://*:5556";
//    public static final long ZMQ_POLL_INTERVAL_MS = 100;
//
//    // Polling configuration
//    public static final long METRICS_COLLECTION_INTERVAL_MS = 60000; // 1 minute
//    public static final long REQUEST_TIMEOUT_MS = 30000; // 30 seconds
//
//    // API path constants
//    public static final String API_BASE_PATH = "/api/v1";
//    public static final String API_CREDENTIAL_PATH = API_BASE_PATH + "/credential";
//    public static final String API_DISCOVERY_PATH = API_BASE_PATH + "/discovery";
//    public static final String API_PROVISIONING_PATH = API_BASE_PATH + "/provisioning";
//    public static final String API_POLLING_PATH = API_BASE_PATH + "/polling";
//
//    // Field names
//    public static final String FIELD_ID = "id";
//    public static final String FIELD_NAME = "name";
//    public static final String FIELD_CREDENTIAL_ID = "credentialId";
//    public static final String FIELD_DISCOVERY_ID = "discoveryId";
//    public static final String FIELD_MONITOR_ID = "monitorId";
//    public static final String FIELD_IP_ADDRESS = "ipAddress";
//    public static final String FIELD_PORT = "portNo";
//    public static final String FIELD_STATUS = "status";
//    public static final String FIELD_MESSAGE = "message";
//    public static final String FIELD_TIMESTAMP = "timestamp";
//    public static final String FIELD_USERNAME = "username";
//    public static final String FIELD_PASSWORD = "password";
//    public static final String FIELD_PROTOCOL = "protocol";
//
//    // Response codes
//    public static final int HTTP_OK = 200;
//    public static final int HTTP_CREATED = 201;
//    public static final int HTTP_BAD_REQUEST = 400;
//    public static final int HTTP_UNAUTHORIZED = 401;
//    public static final int HTTP_FORBIDDEN = 403;
//    public static final int HTTP_NOT_FOUND = 404;
//    public static final int HTTP_CONFLICT = 409;
//    public static final int HTTP_INTERNAL_ERROR = 500;
//
//    // Misc
//    public static final String CONTENT_TYPE_JSON = "application/json";
//    public static final String CHARSET_UTF8 = "UTF-8";
//    public static final int DEFAULT_TIMEOUT_SECONDS = 30;
//    public static final String GO_PLUGIN_PATH = "/modular-plugin/plugin-zmq";
//
//}

package org.nms.utils;

public class Constants
{
    // Database configuration
    public static final int DB_PORT = 5432;
    public static final String DB_HOST = "localhost";
    public static final String DB_NAME = "nms_new";
    public static final String DB_USER = "postgres";
    public static final String DB_PASSWORD = "moontomarsis#2";

    // EventBus addresses
    public static final String DB_EXECUTE_WITHOUT_PARAM_EVENTBUS = "db.execute.no.params";
    public static final String DB_EXECUTE_PARAM_EVENTBUS = "db.execute.params";
    public static final String DB_QUERY = "query";
    public static final String DB_PARAMS = "params";

    // Credential column names
    public static final String ID = "id";
    public static final String CRED_ID = "id";
    public static final String CRED_PROTOCOL = "protocol";
    public static final String CRED_USERNAME = "username";
    public static final String CRED_PASSWORD = "password";
    public static final String CRED_PROFILENAME = "profile_name";
    public static final String CRED_ID_RESPONSE = "credential.profile.id";
    public static final String CRED_NAME_RESPONSE = "credential.profile.name";
    public static final String CRED_PROTOCOL_RESPONSE = "credential.profile.protocol";
    public static final String CRED_USERNAME_RESPONSE = "credential.profile.username";

    // Discovery column names
    public static final String DISC_ID = "discovery_id";
    public static final String DISC_NAME = "discovery_name";
    public static final String DISC_IP_ADDRESS = "ip_address";
    public static final String DISC_PORT_NO = "port_no";
    public static final String DISC_STATUS = "status";
    public static final String DISC_LAST_DISCOVERY_TIME = "lastdiscoverytime";
    public static final String DISC_CREDENTIAL_ID = "credential_id";
    public static final String DISC_MESSAGE = "message";

    // Polling column names
    public static final String POLLING_MONITOR_ID = "monitor_id";
    public static final String POLLING_DATA = "data";
    public static final String POLLING_TIMESTAMP = "timestamp";

    // Provision column names
    public static final String MONITOR_ID = "monitorId";
    public static final String DISCOVERY_ID = "discoveryId";
    public static final String PROVISION_STATUS = "provision_status";
}