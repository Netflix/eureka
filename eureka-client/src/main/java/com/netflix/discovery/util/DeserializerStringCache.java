package com.netflix.discovery.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.cfg.ContextAttributes;

/**
 * A non-locking alternative to {@link String#intern()} and {@link StringCache} that works with DeserializationContext.  Definitely
 * NOT thread-safe.
 *
 */
public class DeserializerStringCache implements Function<String,String>{

    private static final String ATTR_STRING_CACHE = "stringCache";

    public static final int LENGTH_LIMIT = 38;

    private final Map<String, String> cache;
    private final int lengthLimit = LENGTH_LIMIT;

    public static ObjectReader init(ObjectReader reader) {        
       return init(reader, null);   
    }
    
    public static ObjectReader init(ObjectReader reader, DeserializationContext context) {        
        Map<String,String> cache = Optional.ofNullable(context).map(c->(Map<String,String>)c.getAttribute(ATTR_STRING_CACHE)).orElseGet(()->new HashMap<>(10240));
        return reader.withAttribute(ATTR_STRING_CACHE, cache);    
    }
    
    public static Function<String,String> from(DeserializationContext context) {
       Map<String,String> cache = (Map<String,String>)context.getAttribute(ATTR_STRING_CACHE);
       if (cache == null) {
           cache = new HashMap<String,String>();
       }
       return new DeserializerStringCache(cache);
    }
    
    public static void clear(ObjectReader reader) {
        ContextAttributes attributes = reader.getConfig().getAttributes();
        Map<String,String> cache = (Map<String,String>)attributes.getAttribute(ATTR_STRING_CACHE);
        if (cache != null) {
            cache.clear();
        }
    }
    
    public static void clear(DeserializationContext context) {
        Map<String,String> cache = (Map<String,String>)context.getAttribute(ATTR_STRING_CACHE);
        if (cache != null) {
            cache.clear();
        }
    }
    
    private DeserializerStringCache(Map<String,String> cache) {
        this.cache = cache;
    }

    @Override
    public String apply(final String str) {
        if (str != null && (lengthLimit < 0 || str.length() <= lengthLimit)) {
            return cache.computeIfAbsent(str, s->s);
        }
        return str;
    }
    
    public void clear() {
        cache.clear();
    }

    public int size() {
        return cache.size();
    }
}
