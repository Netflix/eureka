package com.netflix.eureka2;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class SimpleGsonClassTypeAdapterTest {
    @Test(timeout = 60000)
    public void simpleTypeAdapter() {
        Gson gson = new GsonBuilder().registerTypeAdapter(Class.class, new SimpleGsonClassTypeAdapter()).create();
        Map<String, String> data = new HashMap<>();
        data.put("name", "Barak Obama");
        data.put("place", "washington DC.");
        final String str = gson.toJson(data);
        Assert.assertTrue(str != null);
        Assert.assertTrue(!str.isEmpty());

        Map<String, Class> classMap = new HashMap<>();
        classMap.put("string", String.class);
        classMap.put("number", Integer.class);

        final String clsStr = gson.toJson(classMap);
        Assert.assertTrue(clsStr != null);
        Assert.assertTrue(!clsStr.isEmpty());
    }

}
