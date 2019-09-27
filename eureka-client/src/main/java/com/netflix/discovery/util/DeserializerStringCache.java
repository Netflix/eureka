package com.netflix.discovery.util;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.PrimitiveIterator.OfInt;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectReader;

/**
 * A non-locking alternative to {@link String#intern()} and {@link StringCache}
 * that works with Jackson's DeserializationContext. Definitely NOT thread-safe,
 * intended to avoid the costs associated with thread synchronization and
 * short-lived heap allocations (e.g., Strings)
 *
 */
public class DeserializerStringCache implements Function<String, String> {

    public enum CacheScope {
        // Strings in this scope are freed on deserialization of each
        // Application element
        APPLICATION_SCOPE,
        // Strings in this scope are freed when overall deserialization is
        // completed
        GLOBAL_SCOPE
    }

    private static final Logger logger = LoggerFactory.getLogger(DeserializerStringCache.class);
    private static final boolean debugLogEnabled = logger.isDebugEnabled();
    private static final String ATTR_STRING_CACHE = "deserInternCache";
    private static final int LENGTH_LIMIT = 256;
    private static final int LRU_LIMIT = 1024 * 40;

    private final Map<CharBuffer, String> globalCache;
    private final Map<CharBuffer, String> applicationCache;
    private final int lengthLimit = LENGTH_LIMIT;

    /**
     * adds a new DeserializerStringCache to the passed-in ObjectReader
     * 
     * @param reader
     * @return a wrapped ObjectReader with the string cache attribute
     */
    public static ObjectReader init(ObjectReader reader) {
        return reader.withAttribute(ATTR_STRING_CACHE, new DeserializerStringCache(
                new HashMap<CharBuffer, String>(2048), new LinkedHashMap<CharBuffer, String>(4096, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Entry<CharBuffer, String> eldest) {
                        return size() > LRU_LIMIT;
                    }

                }));
    }

    /**
     * adds an existing DeserializerStringCache from the DeserializationContext
     * to an ObjectReader
     * 
     * @param reader
     *            a new ObjectReader
     * @param context
     *            an existing DeserializationContext containing a
     *            DeserializerStringCache
     * @return a wrapped ObjectReader with the string cache attribute
     */
    public static ObjectReader init(ObjectReader reader, DeserializationContext context) {
        return withCache(context, cache -> {
            if (cache == null)
                throw new IllegalStateException();
            return reader.withAttribute(ATTR_STRING_CACHE, cache);
        });
    }

    /**
     * extracts a DeserializerStringCache from the DeserializationContext
     * 
     * @param context
     *            an existing DeserializationContext containing a
     *            DeserializerStringCache
     * @return a wrapped ObjectReader with the string cache attribute
     */
    public static DeserializerStringCache from(DeserializationContext context) {
        return withCache(context, cache -> {
            if (cache == null) {
                cache = new DeserializerStringCache(new HashMap<CharBuffer, String>(),
                        new HashMap<CharBuffer, String>());
            }
            return cache;
        });
    }

    /**
     * clears app-scoped cache entries from the specified ObjectReader
     * 
     * @param reader
     */
    public static void clear(ObjectReader reader) {
        clear(reader, CacheScope.APPLICATION_SCOPE);
    }

    /**
     * clears cache entries in the given scope from the specified ObjectReader.
     * Always clears app-scoped entries.
     * 
     * @param reader
     * @param scope
     */
    public static void clear(ObjectReader reader, final CacheScope scope) {
        withCache(reader, cache -> {
            if (scope == CacheScope.GLOBAL_SCOPE) {
                if (debugLogEnabled)
                    logger.debug("clearing global-level cache with size {}", cache.globalCache.size());
                cache.globalCache.clear();
            }
            if (debugLogEnabled)
                logger.debug("clearing app-level serialization cache with size {}", cache.applicationCache.size());
            cache.applicationCache.clear();
            return null;
        });
    }

    /**
     * clears app-scoped cache entries from the specified DeserializationContext
     * 
     * @param context
     */
    public static void clear(DeserializationContext context) {
        clear(context, CacheScope.APPLICATION_SCOPE);
    }

    /**
     * clears cache entries in the given scope from the specified
     * DeserializationContext. Always clears app-scoped entries.
     * 
     * @param context
     * @param scope
     */
    public static void clear(DeserializationContext context, CacheScope scope) {
        withCache(context, cache -> {
            if (scope == CacheScope.GLOBAL_SCOPE) {
                if (debugLogEnabled)
                    logger.debug("clearing global-level serialization cache with size {}", cache.globalCache.size());
                cache.globalCache.clear();
            }
            if (debugLogEnabled)
                logger.debug("clearing app-level serialization cache with size {}", cache.applicationCache.size());
            cache.applicationCache.clear();
            return null;
        });
    }

    private static <T> T withCache(DeserializationContext context, Function<DeserializerStringCache, T> consumer) {
        DeserializerStringCache cache = (DeserializerStringCache) context.getAttribute(ATTR_STRING_CACHE);
        return consumer.apply(cache);
    }

    private static <T> T withCache(ObjectReader reader, Function<DeserializerStringCache, T> consumer) {
        DeserializerStringCache cache = (DeserializerStringCache) reader.getAttributes()
                .getAttribute(ATTR_STRING_CACHE);
        return consumer.apply(cache);
    }

    private DeserializerStringCache(Map<CharBuffer, String> globalCache, Map<CharBuffer, String> applicationCache) {
        this.globalCache = globalCache;
        this.applicationCache = applicationCache;
    }

    public ObjectReader initReader(ObjectReader reader) {
        return reader.withAttribute(ATTR_STRING_CACHE, this);
    }

    /**
     * returns a String read from the JsonParser argument's current position.
     * The returned value may be interned at the app scope to reduce heap
     * consumption
     * 
     * @param jp
     * @return a possibly interned String
     * @throws IOException
     */
    public String apply(final JsonParser jp) throws IOException {
        return apply(jp, CacheScope.APPLICATION_SCOPE, null);
    }

    public String apply(final JsonParser jp, CacheScope cacheScope) throws IOException {
        return apply(jp, cacheScope, null);
    }

    /**
     * returns a String read from the JsonParser argument's current position.
     * The returned value may be interned at the given cacheScope to reduce heap
     * consumption
     * 
     * @param jp
     * @param cacheScope
     * @return a possibly interned String
     * @throws IOException
     */
    public String apply(final JsonParser jp, CacheScope cacheScope, Supplier<String> source) throws IOException {
        return apply(CharBuffer.wrap(jp, source), cacheScope);
    }

    /**
     * returns a String that may be interned at app-scope to reduce heap
     * consumption
     * 
     * @param charValue
     * @return a possibly interned String
     */
    public String apply(final CharBuffer charValue) {
        return apply(charValue, CacheScope.APPLICATION_SCOPE);
    }

    /**
     * returns a object of type T that may be interned at the specified scope to
     * reduce heap consumption
     * 
     * @param charValue
     * @param cacheScope
     * @param trabsform
     * @return a possibly interned instance of T
     */
    public String apply(CharBuffer charValue, CacheScope cacheScope) {
        int keyLength = charValue.length();
        if ((lengthLimit < 0 || keyLength <= lengthLimit)) {
            Map<CharBuffer, String> cache = (cacheScope == CacheScope.GLOBAL_SCOPE) ? globalCache : applicationCache;
            String value = cache.get(charValue);
            if (value == null) {
                value = charValue.consume((k, v) -> {
                    cache.put(k, v);
                });
            } else {
                // System.out.println("cache hit");
            }
            return value;
        }
        return charValue.toString();
    }

    /**
     * returns a String that may be interned at the app-scope to reduce heap
     * consumption
     * 
     * @param stringValue
     * @return a possibly interned String
     */
    @Override
    public String apply(final String stringValue) {
        return apply(stringValue, CacheScope.APPLICATION_SCOPE);
    }

    /**
     * returns a String that may be interned at the given scope to reduce heap
     * consumption
     * 
     * @param stringValue
     * @param cacheScope
     * @return a possibly interned String
     */
    public String apply(final String stringValue, CacheScope cacheScope) {
        if (stringValue != null && (lengthLimit < 0 || stringValue.length() <= lengthLimit)) {
            return (String) (cacheScope == CacheScope.GLOBAL_SCOPE ? globalCache : applicationCache)
                    .computeIfAbsent(CharBuffer.wrap(stringValue), s -> {
                        logger.trace(" (string) writing new interned value {} into {} cache scope", stringValue, cacheScope);
                        return stringValue;
                    });
        }
        return stringValue;
    }

    public int size() {
        return globalCache.size() + applicationCache.size();
    }

    private interface CharBuffer {
        static final int DEFAULT_VARIANT = -1;

        public static CharBuffer wrap(JsonParser source, Supplier<String> stringSource) throws IOException {
            return new ArrayCharBuffer(source, stringSource);
        }

        public static CharBuffer wrap(JsonParser source) throws IOException {
            return new ArrayCharBuffer(source);
        }

        public static CharBuffer wrap(String source) {
            return new StringCharBuffer(source);
        }

        String consume(BiConsumer<CharBuffer, String> valueConsumer);

        int length();

        int variant();

        OfInt chars();

        static class ArrayCharBuffer implements CharBuffer {
            private final char[] source;
            private final int offset;
            private final int length;
            private final Supplier<String> valueTransform;
            private final int variant;
            private final int hash;

            ArrayCharBuffer(JsonParser source) throws IOException {
                this(source, null);
            }

            ArrayCharBuffer(JsonParser source, Supplier<String> valueTransform) throws IOException {
                this.source = source.getTextCharacters();
                this.offset = source.getTextOffset();
                this.length = source.getTextLength();
                this.valueTransform = valueTransform;
                this.variant = valueTransform == null ? DEFAULT_VARIANT : System.identityHashCode(valueTransform.getClass());
                this.hash =  31 * arrayHash(this.source, offset, length) + variant;
            }

            @Override
            public int length() {
                return length;
            }

            @Override
            public int variant() {
                return variant;
            }

            @Override
            public int hashCode() {
                return hash;
            }

            @Override
            public boolean equals(Object other) {
                if (other instanceof CharBuffer) {
                    CharBuffer otherBuffer = (CharBuffer) other;
                    if (otherBuffer.length() == length) {
                        if (otherBuffer.variant() == variant) {
                            OfInt otherText = otherBuffer.chars();
                            for (int i = offset; i < length; i++) {
                                if (source[i] != otherText.nextInt()) {
                                    return false;
                                }
                            }
                        return true;
                        }
                    }
                }
                return false;
            }

            @Override
            public OfInt chars() {
                return new OfInt() {
                    int index = offset;
                    int limit = index + length;

                    @Override
                    public boolean hasNext() {
                        return index < limit;
                    }

                    @Override
                    public int nextInt() {
                        return source[index++];
                    }
                };
            }

            @Override
            public String toString() {
                return valueTransform  == null ? new String(this.source, offset, length) : valueTransform.get();
            }

            @Override
            public String consume(BiConsumer<CharBuffer, String> valueConsumer) {
                String key = new String(this.source, offset, length);
                String value = valueTransform == null ? key : valueTransform.get();
                valueConsumer.accept(new StringCharBuffer(key, variant), value);
                return value;
            }

            private static int arrayHash(char[] a, int offset, int length) {
                if (a == null)
                    return 0;
                int result = 0;
                int limit = offset + length;
                for (int i = offset; i < limit; i++) {
                    result = 31 * result + a[i];
                }
                return result;
            }
        }

        static class StringCharBuffer implements CharBuffer {
            private final String source;
            private final int variant;
            private final int hashCode;

            StringCharBuffer(String source) {
                this(source, -1);
            }

            StringCharBuffer(String source, int variant) {
                this.source = source;
                this.variant = variant;
                this.hashCode = 31 * source.hashCode() + variant;
            }            

            @Override
            public int hashCode() {
                return hashCode;
            }

            @Override
            public int variant() {
                return variant;
            }

            @Override
            public boolean equals(Object other) {
                if (other instanceof CharBuffer) {
                    CharBuffer otherBuffer = (CharBuffer) other;
                    if (otherBuffer.variant() == variant) {
                        int length = source.length();
                        if (otherBuffer.length() == length) {
                            OfInt otherText = otherBuffer.chars();
                            for (int i = 0; i < length; i++) {
                                if (source.charAt(i) != otherText.nextInt()) {
                                    return false;
                                }
                            }
                            return true;
                        }
                    }
                }
                return false;
            }

            @Override
            public int length() {
                return source.length();
            }

            @Override
            public String toString() {
                return source;
            }

            @Override
            public OfInt chars() {
                return new OfInt() {
                    int index;

                    @Override
                    public boolean hasNext() {
                        return index < source.length();
                    }

                    @Override
                    public int nextInt() {
                        return source.charAt(index++);
                    }
                };
            }

            @Override
            public String consume(BiConsumer<CharBuffer, String> valueConsumer) {
                valueConsumer.accept(this, source);
                return source;
            }
        }

    }
}
