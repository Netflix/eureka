package com.netflix.eureka2.eureka1x.rest.codec;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author Tomasz Bak
 */
public class CachingEureka1xDataCodec implements Eureka1xDataCodec {

    private final Eureka1xDataCodec delegate;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final WeakHashMap<CachedObjectReference, WeakReference<byte[]>> cache = new WeakHashMap<>();

    public CachingEureka1xDataCodec(Eureka1xDataCodec delegate) {
        this.delegate = delegate;
    }

    @Override
    public byte[] encode(Object entity, EncodingFormat format, boolean gzip) throws IOException {
        CachedObjectReference key = new CachedObjectReference(entity, format, gzip);
        lock.readLock().lock();
        try {
            if (cache.get(key) != null) {
                return cache.get(key).get();
            }
        } finally {
            lock.readLock().unlock();
        }

        lock.writeLock().lock();
        try {
            if (cache.get(key) == null) {
                byte[] bytes = delegate.encode(entity, format, gzip);
                cache.put(key, new WeakReference<byte[]>(bytes));
                return bytes;
            } else {
                return cache.get(key).get();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public <T> T decode(byte[] bytes, Class<T> bodyType, EncodingFormat format) throws IOException {
        return delegate.decode(bytes, bodyType, format);
    }

    static class CachedObjectReference {
        private final Object ref;
        private final EncodingFormat format;
        private final boolean gzip;

        CachedObjectReference(Object ref, EncodingFormat format, boolean gzip) {
            this.ref = ref;
            this.format = format;
            this.gzip = gzip;
        }

        public Object getRef() {
            return ref;
        }

        public EncodingFormat getFormat() {
            return format;
        }

        public boolean isGzip() {
            return gzip;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;

            CachedObjectReference that = (CachedObjectReference) o;

            if (gzip != that.gzip)
                return false;
            if (format != that.format)
                return false;
            if (ref != that.ref) // We compare references, not content
                return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = ref != null ? ref.hashCode() : 0;
            result = 31 * result + (format != null ? format.hashCode() : 0);
            result = 31 * result + (gzip ? 1 : 0);
            return result;
        }
    }
}
