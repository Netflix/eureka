package com.netflix.discovery.util;

import java.io.IOException;
import java.nio.CharBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.RuntimeJsonMappingException;
import com.fasterxml.jackson.databind.cfg.ContextAttributes;

/**
 * A non-locking alternative to {@link String#intern()} and {@link StringCache}
 * that works with Jackson's DeserializationContext. Definitely NOT thread-safe,
 * intended to avoid the costs associated with thread synchronization
 *
 */
public class DeserializerStringCache implements Function<String, String> {
    private static final Logger logger = LoggerFactory.getLogger(DeserializerStringCache.class);

    public enum CacheScope {
        // Strings in this scope are freed on deserialization of each
        // Application element
        APPLICATION_SCOPE,
        // Strings in this scope are freed when overall deserialization is
        // completed
        GLOBAL_SCOPE
    }

    private static final String ATTR_STRING_CACHE = "globalStringInternCache";
    private static final String ATTR_APPL_STRING_CACHE = "appStringInternCache";

    public static final int LENGTH_LIMIT = 38;

    private final Map<CharBuffer, Object> globalCache;
    private final Map<CharBuffer, Object> applicationCache;
    private final int lengthLimit = LENGTH_LIMIT;

    public static ObjectReader init(ObjectReader reader) {
        return reader.withAttribute(ATTR_STRING_CACHE, new HashMap<CharBuffer, String>())
                .withAttribute(ATTR_APPL_STRING_CACHE, new HashMap<CharBuffer, String>());
    }

    public static ObjectReader init(ObjectReader reader, DeserializationContext context) {
        return withCache(context.getConfig().getAttributes(), (globalCache, appCache) -> {
            if (globalCache == null || appCache == null)
                throw new IllegalStateException();
            return reader.withAttribute(ATTR_STRING_CACHE, globalCache).withAttribute(ATTR_APPL_STRING_CACHE, appCache);
        });
    }

    public static DeserializerStringCache from(DeserializationContext context) {
        return withCache(context.getConfig().getAttributes(), (globalCache, appCache) -> {
            return new DeserializerStringCache(Optional.ofNullable(globalCache).orElseGet(HashMap::new),
                    Optional.ofNullable(appCache).orElseGet(HashMap::new));
        });
    }

    public static void clear(ObjectReader reader) {
        clear(reader, CacheScope.APPLICATION_SCOPE);
    }

    public static void clear(ObjectReader reader, final CacheScope scope) {
        withCache(reader.getAttributes(), (g, c) -> {
            if (scope == CacheScope.GLOBAL_SCOPE) {
                if (logger.isTraceEnabled())
                    logger.trace("clearing global-level cache");
                Optional.ofNullable(g).get().clear();
            }
            Optional.ofNullable(c).ifPresent(appCache -> {
                if (logger.isTraceEnabled())
                    logger.trace("clearing app-level serialization cache");
                appCache.clear();
            });
            return null;
        });
    }

    public static void clear(DeserializationContext context) {
        clear(context, CacheScope.APPLICATION_SCOPE);
    }

    public static void clear(DeserializationContext context, CacheScope scope) {
        withCache(context.getConfig().getAttributes(), (g, c) -> {
            if (scope == CacheScope.GLOBAL_SCOPE) {
                if (logger.isTraceEnabled())
                    logger.trace("clearing global-level serialization cache");
                Optional.ofNullable(g).get().clear();
            }
            Optional.ofNullable(c).ifPresent(appCache -> {
                if (logger.isTraceEnabled())
                    logger.trace("clearing app-level serialization cache");
                appCache.clear();
            });
            return null;
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T withCache(ContextAttributes attributes,
            BiFunction<Map<CharBuffer, Object>, Map<CharBuffer, Object>, T> consumer) {
        Map<CharBuffer, Object> globalCache = (Map<CharBuffer, Object>) attributes.getAttribute(ATTR_STRING_CACHE);
        Map<CharBuffer, Object> appCache = (Map<CharBuffer, Object>) attributes.getAttribute(ATTR_APPL_STRING_CACHE);
        return consumer.apply(globalCache, appCache);
    }

    private DeserializerStringCache(Map<CharBuffer, Object> globalCache, Map<CharBuffer, Object> applicationCache) {
        this.globalCache = globalCache;
        this.applicationCache = applicationCache;
    }

    public ObjectReader initReader(ObjectReader reader) {
        return reader.withAttribute(ATTR_STRING_CACHE, this.globalCache).withAttribute(ATTR_APPL_STRING_CACHE,
                this.applicationCache);
    }

    public String apply(final JsonParser jp) throws IOException {
        return apply(jp, CacheScope.APPLICATION_SCOPE);
    }

    public String apply(final JsonParser jp, CacheScope cacheScope) throws IOException {
        return apply(jp, cacheScope, c -> {
            try {
                return jp.getValueAsString();
            } catch (IOException e) {
                throw new RuntimeJsonMappingException(e.getMessage());
            }
        });
    }

    public <T> T apply(final JsonParser jp, CacheScope cacheScope, Function<CharBuffer, T> transform)
            throws IOException {
        int length = jp.getTextLength();
        CharBuffer value = CharBuffer.wrap(jp.getTextCharacters(), jp.getTextOffset(), length);
        return (length <= lengthLimit) ? apply(value, cacheScope, transform) : transform.apply(value);
    }

    public String apply(final CharBuffer charValue) {
        return apply(charValue, CacheScope.APPLICATION_SCOPE);
    }

    public String apply(final CharBuffer charValue, CacheScope cacheScope) {
        return apply(charValue, cacheScope, CharBuffer::toString);
    }

    public <T> T apply(final CharBuffer charValue, Function<CharBuffer, T> transform) {
        return apply(charValue, CacheScope.APPLICATION_SCOPE, transform);
    }

    public <T> T apply(CharBuffer charValue, CacheScope cacheScope, Function<CharBuffer, T> transform) {
        if ((lengthLimit < 0 || charValue.length() <= lengthLimit)) {
            Map<CharBuffer, Object> cache = (cacheScope == CacheScope.GLOBAL_SCOPE) ? globalCache : applicationCache;
            T value = (T) cache.get(charValue);
            if (value == null) {
                value = transform.apply(charValue);
                if (value instanceof String) {
                    charValue = CharBuffer.wrap((String) value);
                } else {
                    CharBuffer cb = CharBuffer.allocate(charValue.length());
                    System.arraycopy(charValue.array(), 0, cb.array(), 0, charValue.length());
                }
                if (logger.isTraceEnabled())
                    logger.trace(" (charbuffer) writing new interned value {} into {} cache scope", value, cacheScope);
                cache.put(charValue, value);
            } else {
                if (logger.isTraceEnabled())
                    logger.trace(" (charbuffer) using interned value {} from {} cache scope", value, cacheScope);
            }
            return value;
        }
        return transform.apply(charValue);
    }

    @Override
    public String apply(final String stringValue) {
        return apply(stringValue, CacheScope.APPLICATION_SCOPE);
    }

    public String apply(final String stringValue, CacheScope cacheScope) {
        if (stringValue != null && (lengthLimit < 0 || stringValue.length() <= lengthLimit)) {
            return (String) (cacheScope == CacheScope.GLOBAL_SCOPE ? globalCache : applicationCache)
                    .computeIfAbsent(CharBuffer.wrap(stringValue), s -> {
                        if (logger.isTraceEnabled())
                            logger.trace(" (string) writing new interned value {} into {} cache scope", stringValue,
                                    cacheScope);
                        return stringValue;
                    });
        }
        return stringValue;
    }

    public int size() {
        return globalCache.size() + applicationCache.size();
    }
}
