package com.netflix.eureka;

import java.util.HashSet;
import java.util.Set;

/**
 * @author David Liu
 */
public class Sets {

    public static <T> HashSet<T> asSet(T... a) {
        HashSet<T> result = new HashSet<T>();
        for (T data : a) {
            result.add(data);
        }
        return result;
    }
}
