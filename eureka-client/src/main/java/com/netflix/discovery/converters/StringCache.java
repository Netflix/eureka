package com.netflix.discovery.converters;

import java.util.Map;
import java.util.WeakHashMap;

/**
* @author Tomasz Bak
*/
public class StringCache {

    public static final int LENGTH_LIMIT = 38;

    private final Map<String, String> cache = new WeakHashMap<>();
    private final int lengthLimit;

    public StringCache() {
        this(LENGTH_LIMIT);
    }

    public StringCache(int lengthLimit) {
        this.lengthLimit = lengthLimit;
    }

    public String cachedValueOf(final String str) {
        if (str != null && (lengthLimit < 0 || str.length() <= lengthLimit)) {
            String s;
            synchronized (cache) {
                s = cache.get(str);
                if (s == null) {
                    cache.put(str, str);
                    s = str;
                }
            }
            return s;
        }
        return str;
    }
}
