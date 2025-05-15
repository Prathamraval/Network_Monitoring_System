package org.nms.service;

import io.vertx.core.json.JsonObject;
import org.nms.database.queries.CredentialQueries;
import org.nms.utils.Constants;

import java.util.function.Function;

public class CredentialService extends BaseService<JsonObject>
{
    public static final String CREDENTIAL_ID = "id";

    public static final String[] CREATE_PARAM_MAPPING = {
            Constants.CRED_PROFILENAME,
            Constants.CRED_PROTOCOL,
            Constants.CRED_USERNAME,
            Constants.CRED_PASSWORD
    };

    public static final String[] UPDATE_PARAM_MAPPING = {
            Constants.CRED_PROFILENAME,
            Constants.CRED_PROTOCOL,
            Constants.CRED_USERNAME,
            Constants.CRED_PASSWORD,
            Constants.CRED_ID
    };

    @Override
    protected String getInsertQuery()
    {
        return CredentialQueries.INSERT_CREDENTIAL_PROFILE;
    }

    @Override
    protected String getSelectAllQuery()
    {
        return CredentialQueries.SELECT_ALL_CREDENTIAL_PROFILES;
    }

    @Override
    protected String getSelectByIdQuery()
    {
        return CredentialQueries.SELECT_CREDENTIAL_PROFILE_BY_ID;
    }

    @Override
    protected String getUpdateQuery()
    {
        return CredentialQueries.UPDATE_CREDENTIAL_PROFILE;
    }

    @Override
    protected String getDeleteQuery()
    {
        return CredentialQueries.DELETE_CREDENTIAL_PROFILE;
    }

    @Override
    protected String getIdField()
    {
        return CREDENTIAL_ID;
    }

    @Override
    protected String[] getJsonToParamsCreateMapping()
    {
        return CREATE_PARAM_MAPPING;
    }

    @Override
    protected String[] getJsonToParamsUpdateMapping()
    {
        return UPDATE_PARAM_MAPPING;
    }

    @Override
    protected Function<JsonObject, JsonObject> getResponseMapper()
    {
        return json -> new JsonObject()
                .put(Constants.CRED_NAME_RESPONSE, json.getString(Constants.CRED_PROFILENAME))
                .put(Constants.CRED_PROTOCOL_RESPONSE, json.getString(Constants.CRED_PROTOCOL));
    }

    @Override
    protected Function<JsonObject, JsonObject> getRowToResponseMapper()
    {
        return row -> new JsonObject()
                .put(Constants.CRED_NAME_RESPONSE, row.getString(Constants.CRED_PROFILENAME))
                .put(Constants.CRED_PROTOCOL_RESPONSE, row.getString(Constants.CRED_PROTOCOL))
                .put(Constants.CRED_USERNAME_RESPONSE, row.getString(Constants.CRED_USERNAME))
                .put(Constants.CRED_ID_RESPONSE, row.getLong(Constants.CRED_ID));
    }
}


//    // Parameter mappings for database queries
//    private static final List<String> CREATE_PARAMS = List.of(
//            FIELD_PROFILE_NAME, FIELD_PROTOCOL, FIELD_USERNAME, FIELD_PASSWORD
//    );
//
//    private static final List<String> UPDATE_PARAMS = List.of(
//            FIELD_PROFILE_NAME, FIELD_PROTOCOL, FIELD_USERNAME, FIELD_PASSWORD, FIELD_ID
//    );
