package com.netflix.eureka.registry;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPOutputStream;

import com.netflix.servo.monitor.Stopwatch;

/**
 * @author David Liu
 */
public interface ResponseCache {

    void invalidate(String appName, @Nullable String vipAddress, @Nullable String secureVipAddress);

    AtomicLong getVersionDelta();

    AtomicLong getVersionDeltaWithRegions();

    /**
     * Get the cached information about applications.
     *
     * <p>
     * If the cached information is not available it is generated on the first
     * request. After the first request, the information is then updated
     * periodically by a background thread.
     * </p>
     *
     * @param key the key for which the cached information needs to be obtained.
     * @return payload which contains information about the applications.
     */
    CacheValue get(Key key);

    /**
     * Get the compressed information about the applications.
     *
     * @param key the key for which the compressed cached information needs to be obtained.
     * @return compressed payload which contains information about the applications.
     */
    CacheValue getGZIP(Key key);

    class CacheValue {
        /**
         * Monotonically increasing ids for entity identification purposes (ETag)
         */
        private final long id;
        private final long timestamp;

        private final byte[] payload;
        private final byte[] gzipped;
        private final String etag;

        protected CacheValue(String source, long id, long timestamp, byte[] payload, byte[] gzipped) {
            this.id = id;
            this.timestamp = timestamp;
            this.payload = payload;
            this.gzipped = gzipped;
            this.etag = "source=" + source + ",id=" + id + ",timestamp=" + timestamp;
        }

        public long getId() {
            return id;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public byte[] getPayload() {
            return payload;
        }

        public byte[] getGzipped() {
            return gzipped;
        }

        public String getETag() {
            return etag;
        }
    }
}
