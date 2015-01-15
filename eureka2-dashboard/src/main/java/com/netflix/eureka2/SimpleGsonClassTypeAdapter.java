package com.netflix.eureka2;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;

public class SimpleGsonClassTypeAdapter implements JsonSerializer<Class> {
    @Override
    public JsonElement serialize(Class src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject result = new JsonObject();
        result.addProperty("class", src.getName());
        return result;
    }
}
