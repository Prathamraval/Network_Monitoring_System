package org.nms.utils;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Tuple;

import java.util.logging.Logger;

public class DbUtil
{
    public static Tuple jsonArrayToTuple(JsonArray jsonArray)
    {
        var tuple = Tuple.tuple();
        for (var i = 0; i < jsonArray.size(); i++)
        {
            tuple.addValue(jsonArray.getValue(i));
        }
        return tuple;
    }

    public static JsonArray jsonToJsonArray(JsonObject json, String[] keys)
    {
        var array = new JsonArray();
        for (var key : keys)
        {
            array.add(json.getValue(key));
        }
        return array;
    }
}