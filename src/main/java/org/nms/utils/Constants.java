package org.nms.utils;

import java.time.ZoneId;

public class Constants
{

    public static final String DATABASE = "DATABASE";
    public static final String HTTP = "HTTP";
    public static final String ZMQ = "ZMQ";
    public static final String METRICS = "METRICS";
    public static final String GO_PLUGIN_PATH = "/plugin-final1/plugin-zmq";


    // EventBus addresses
    public static final String DB_EXECUTE_EVENTBUS = "db.execute";
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
    public static final String STATUS = "status";
    public static final String DISC_LAST_DISCOVERY_TIME = "lastdiscoverytime";
    public static final String DISC_CREDENTIAL_ID = "credential_id";
    public static final String MESSAGE = "message";
    public static final String DISC_WAIT_TIME = "wait_time";

    // Polling column names
    public static final String MONITOR_ID = "monitor_id";
    public static final String POLLING_DATA = "data";
    public static final String POLLING_TIMESTAMP = "timestamp";

    // Provision column names
    public static final String DISCOVERY_ID = "discoveryId";
    public static final String PROVISION_STATUS = "provision_status";
    public static final String EVENT_PROVISION_CHANGED = "provision.changed";


    public static final String REQUEST_ID = "request_id";
    public static final String COMMAND = "command";
    public static final String COMMAND_METRICS ="metrics";
    public static final String COMMAND_DISCOVERY = "discovery";

    public static final String DATA = "data";
    public static final String ERROR = "error";
    public static final String STATUS_CODE = "statusCode";

    public static final String ROWS = "rows";
    public static final String ROW_COUNT = "rowCount";

    public static final String CONTENT_TYPE = "content-type";
    public static final String CONTENT = "application/json";
    public static final String TYPE ="type";
    public static final String PROVISIONS = "provisions";
    public static final String LAST_POLL = "last_poll";
    public static final String ACTION = "action";

    public static final String CONFIG_FILE_PATH = "/home/pratham/Desktop/NMS-generic/NMS new/src/main/java/org/nms/config/config.json";
    public static final String SUCCESS = "success";
    public static final String DETAILS = "details";
    public static final String ENTITY = "entity";
    public static final String REPLY_ADDRESS = "replyaddress";
    public static final String RESULT = "result";

    public static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

    public static final String PROMISE_ID = "promiseId";
}