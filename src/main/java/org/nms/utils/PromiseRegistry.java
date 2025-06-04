package org.nms.utils;

import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class PromiseRegistry
{
    private static final PromiseRegistry INSTANCE = new PromiseRegistry();
    private final ConcurrentMap<String, Promise<JsonObject>> promiseMap = new ConcurrentHashMap<>();

    private PromiseRegistry() {}

    public static PromiseRegistry getInstance() {
        return INSTANCE;
    }

    public void registerPromise(String promiseId, Promise<JsonObject> promise) {
        promiseMap.put(promiseId, promise);
    }

    public Promise<JsonObject> getPromise(String promiseId) {
        return promiseMap.get(promiseId);
    }

    public void removePromise(String promiseId) {
        promiseMap.remove(promiseId);
    }
}
