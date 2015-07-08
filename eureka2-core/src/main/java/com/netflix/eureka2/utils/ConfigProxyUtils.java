package com.netflix.eureka2.utils;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Tomasz Bak
 */
public final class ConfigProxyUtils {

    private static final Object[] EMPTY_ARRAY = new Object[0];

    private ConfigProxyUtils() {
    }

    public static <T> Map<String, Object> asMap(T config) {
        Map<String, Object> resultMap = new HashMap<>();
        for (Class<?> interfClass : config.getClass().getInterfaces()) {
            for (Method method : interfClass.getMethods()) {
                String name = method.getName();
                String propertyName;
                if ((name.startsWith("get") || name.startsWith("has")) && name.length() > 3) {
                    propertyName = Character.toLowerCase(name.charAt(3)) + name.substring(4);
                } else if (name.startsWith("is") && name.length() > 2) {
                    propertyName = Character.toLowerCase(name.charAt(2)) + name.substring(3);
                } else {
                    continue;
                }
                try {
                    resultMap.put(propertyName, method.invoke(config, EMPTY_ARRAY));
                } catch (Exception e) {
                    throw new IllegalStateException("Cannot access property " + propertyName + " on class " + config.getClass(), e);
                }
            }
        }
        return resultMap;
    }
}
