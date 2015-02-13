package com.netflix.eureka2.utils;

/**
 * @author Tomasz Bak
 */
public class Asserts {

    public static <T> void assertNonNull(T value, String parameterName) {
        if (value == null) {
            throw new IllegalArgumentException("Non null value expected for parameter " + parameterName);
        }
    }
}
