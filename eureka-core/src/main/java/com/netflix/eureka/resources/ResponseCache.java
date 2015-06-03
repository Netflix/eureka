/*
 * Copyright 2012 Netflix, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.netflix.eureka.resources;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPOutputStream;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Supplier;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.converters.EurekaJacksonCodec;
import com.netflix.discovery.converters.XmlXStream;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.eureka.AbstractInstanceRegistry;
import com.netflix.eureka.CurrentRequestVersion;
import com.netflix.eureka.EurekaServerConfig;
import com.netflix.eureka.EurekaServerConfigurationManager;
import com.netflix.eureka.PeerAwareInstanceRegistryImpl;
import com.netflix.eureka.Version;
import com.netflix.servo.annotations.DataSourceType;
import com.netflix.servo.annotations.Monitor;
import com.netflix.servo.monitor.Monitors;
import com.netflix.servo.monitor.Stopwatch;
import com.netflix.servo.monitor.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The class that is responsible for caching registry information that will be
 * queried by the clients.
 *
 * <p>
 * The cache is maintained in compressed and non-compressed form for three
 * categories of requests - all applications, delta changes and for individual
 * applications. The compressed form is probably the most efficient in terms of
 * network traffic especially when querying all applications.
 *
 * The cache also maintains separate pay load for <em>JSON</em> and <em>XML</em>
 * formats and for multiple versions too.
 * </p>
 *
 * @author Karthik Ranganathan, Greg Kim
 */
public class ResponseCache {

    private static final Logger logger = LoggerFactory
            .getLogger(ResponseCache.class);
    private final EurekaServerConfig eurekaConfig = EurekaServerConfigurationManager
            .getInstance().getConfiguration();

    public static final String ALL_APPS = "ALL_APPS";
    public static final String ALL_APPS_DELTA = "ALL_APPS_DELTA";

    private final Timer serializeAllAppsTimer = Monitors
            .newTimer("serialize-all");
    private final Timer serializeDeltaAppsTimer = Monitors
            .newTimer("serialize-all-delta");
    private final Timer serializeAllAppsWithRemoteRegionTimer = Monitors
            .newTimer("serialize-all_remote_region");
    private final Timer serializeDeltaAppsWithRemoteRegionTimer = Monitors
            .newTimer("serialize-all-delta_remote_region");
    private final Timer serializeOneApptimer = Monitors
            .newTimer("serialize-one");
    private final Timer serializeViptimer = Monitors.newTimer("serialize-one-vip");
    private final Timer compressPayloadTimer = Monitors
            .newTimer("compress-payload");

    private static final AtomicLong versionDelta = new AtomicLong(0);
    private static final AtomicLong versionDeltaWithRegions = new AtomicLong(0);
    private static final String EMPTY_PAYLOAD = "";

    public enum KeyType {
        JSON, XML
    }

    /**
     * This map holds mapping of keys without regions to a list of keys with region (provided by clients)
     * Since, during invalidation, triggered by a change in registry for local region, we do not know the regions
     * requested by clients, we use this mapping to get all the keys with regions to be invalidated.
     * If we do not do this, any cached user requests containing region keys will not be invalidated and will stick
     * around till expiry. Github issue: https://github.com/Netflix/eureka/issues/118
     */
    private final Multimap<Key, Key> regionSpecificKeys =
            Multimaps.newListMultimap(new ConcurrentHashMap<Key, Collection<Key>>(), new Supplier<List<Key>>() {
                @Override
                public List<Key> get() {
                    return new CopyOnWriteArrayList<Key>();
                }
            });

    private final ConcurrentMap<Key, Value> readOnlyCacheMap = new ConcurrentHashMap<Key, Value>();
    private static final java.util.Timer timer = new java.util.Timer("Eureka -CacheFillTimer", true);

    private final LoadingCache<Key, Value> readWriteCacheMap =
            CacheBuilder.newBuilder().initialCapacity(1000)
                    .expireAfterWrite(eurekaConfig.getResponseCacheAutoExpirationInSeconds(), TimeUnit.SECONDS)
                    .removalListener(new RemovalListener<Key, Value>() {
                        @Override
                        public void onRemoval(RemovalNotification<Key, Value> notification) {
                            Key removedKey = notification.getKey();
                            if (removedKey.hasRegions()) {
                                Key cloneWithNoRegions = removedKey.cloneWithoutRegions();
                                regionSpecificKeys.remove(cloneWithNoRegions, removedKey);
                            }
                        }
                    })
                    .build(new CacheLoader<Key, Value>() {
                        @Override
                        public Value load(Key key) throws Exception {
                            if (key.hasRegions()) {
                                Key cloneWithNoRegions = key.cloneWithoutRegions();
                                regionSpecificKeys.put(cloneWithNoRegions, key);
                            }
                            Value value = generatePayload(key);
                            return value;
                        }
                    });

    private static final ResponseCache s_instance = new ResponseCache();

    private ResponseCache() {
        long responseCacheUpdateIntervalMs = eurekaConfig.getResponseCacheUpdateIntervalMs();
        timer.schedule(getCacheUpdateTask(),
                new Date(((System.currentTimeMillis() / responseCacheUpdateIntervalMs) * responseCacheUpdateIntervalMs)
                        + responseCacheUpdateIntervalMs),
                responseCacheUpdateIntervalMs);
        try {
            Monitors.registerObject(this);

        } catch (Throwable e) {
            logger.warn(
                    "Cannot register the JMX monitor for the InstanceRegistry :",
                    e);
        }
    }

    private TimerTask getCacheUpdateTask() {
        return new TimerTask() {
            @Override
            public void run() {
                logger.debug("Updating the client cache from response cache");
                for (Key key : readOnlyCacheMap.keySet()) {
                    if (logger.isDebugEnabled()) {
                        Object[] args = {key.getEntityType(), key.getName(), key.getVersion(), key.getType()};
                        logger.debug("Updating the client cache from response cache for key : {} {} {} {}", args);
                    }
                    try {
                        CurrentRequestVersion.set(key.getVersion());
                        Value cacheValue = readWriteCacheMap.get(key);
                        Value currentCacheValue = readOnlyCacheMap.get(key);
                        if (cacheValue != currentCacheValue) {
                            readOnlyCacheMap.put(key, cacheValue);
                        }
                    } catch (Throwable th) {
                        logger.error("Error while updating the client cache from response cache", th);
                    }
                }
            }
        };
    }


    public static ResponseCache getInstance() {
        return s_instance;
    }

    /**
     * Get the cached information about applications.
     *
     * <p>
     * If the cached information is not available it is generated on the first
     * request. After the first request, the information is then updated
     * periodically by a background thread.
     * </p>
     *
     * @param key
     *            the key for which the cached information needs to be obtained.
     * @return payload which contains information about the applications.
     */
    public String get(final Key key) {
        return get(key, false);
    }

    @VisibleForTesting
    String get(final Key key, boolean ignoreReadOnlyCache) {
        Value payload = getValue(key, ignoreReadOnlyCache);
        if (payload == null || payload.getPayload() == EMPTY_PAYLOAD) {
            return null;
        } else {
            return payload.getPayload();
        }
    }

    /**
     * Get the compressed information about the applications.
     *
     * @param key
     *            the key for which the compressed cached information needs to
     *            be obtained.
     * @return compressed payload which contains information about the
     *         applications.
     */
    public byte[] getGZIP(Key key) {
        Value payload = getValue(key, false);
        if (payload == null) {
            return null;
        }
        return payload.getGzipped();
    }

    /**
     * Invalidate the cache of a particular application.
     *
     * @param appName
     *            the application name of the application.
     */
    public void invalidate(String appName, @Nullable String vipAddress, @Nullable String secureVipAddress) {
        for (KeyType type : KeyType.values()) {
            for (Version v : Version.values()) {
                invalidate(new Key(Key.EntityType.Application, appName, type, v),
                        new Key(Key.EntityType.Application, ALL_APPS, type, v),
                        new Key(Key.EntityType.Application, ALL_APPS_DELTA, type, v));
                if (null != vipAddress) {
                    invalidate(new Key(Key.EntityType.VIP, vipAddress, type, v));
                }
                if (null != secureVipAddress) {
                    invalidate(new Key(Key.EntityType.SVIP, secureVipAddress, type, v));
                }
            }
        }
    }

    /**
     * Invalidate the cache information given the list of keys.
     *
     * @param keys
     *            the list of keys for which the cache information needs to be
     *            invalidated.
     */
    public void invalidate(Key... keys) {
        for (Key key : keys) {
            Object[] args = {key.getEntityType(), key.getName(), key.getVersion(), key.getType()};
            logger.debug("Invalidating the response cache key : {} {} {} {}", args);
            readWriteCacheMap.invalidate(key);
            Collection<Key> keysWithRegions = regionSpecificKeys.get(key);
            if (null != keysWithRegions && !keysWithRegions.isEmpty()) {
                for (Key keysWithRegion : keysWithRegions) {
                    logger.debug("Invalidating the response cache key : {} {} {} {}", args);
                    readWriteCacheMap.invalidate(keysWithRegion);
                }
            }
        }
    }

    /**
     * Gets the version number of the cached data.
     *
     * @return teh version number of the cached data.
     */
    public static AtomicLong getVersionDelta() {
        return versionDelta;
    }

    /**
     * Gets the version number of the cached data with remote regions.
     *
     * @return teh version number of the cached data with remote regions.
     */
    public static AtomicLong getVersionDeltaWithRegions() {
        return versionDeltaWithRegions;
    }

    /**
     * Get the number of items in the response cache.
     *
     * @return int value representing the number of items in response cache.
     */
    @Monitor(name = "responseCacheSize", type = DataSourceType.GAUGE)
    public int getCurrentSize() {
        return readWriteCacheMap.asMap().size();
    }

    /**
     * Get the payload in both compressed and uncompressed form.
     */
    @VisibleForTesting
    Value getValue(final Key key, boolean ignoreReadOnlyCache) {
        Value payload = null;
        try {
            if (ignoreReadOnlyCache) {
                payload = readWriteCacheMap.get(key);
            } else {
                final Value currentPayload = readOnlyCacheMap.get(key);
                if (currentPayload != null) {
                    payload = currentPayload;
                } else {
                    payload = readWriteCacheMap.get(key);
                    readOnlyCacheMap.put(key, payload);
                }
            }
        } catch (Throwable t) {
            logger.error("Cannot get value for key :" + key, t);
        }
        return payload;
    }

    /**
     * Generate pay load with both JSON and XML formats for all applications.
     */
    private static String getPayLoad(Key key, Applications apps) {
        if (key.getType() == KeyType.JSON) {
            return EurekaJacksonCodec.getInstance().writeToString(apps);
        } else {
            return XmlXStream.getInstance().toXML(apps);
        }
    }

    /**
     * Generate pay load with both JSON and XML formats for a given application.
     */
    private static String getPayLoad(Key key, Application app) {
        if (app == null) {
            return EMPTY_PAYLOAD;
        }

        if (key.getType() == KeyType.JSON) {
            return EurekaJacksonCodec.getInstance().writeToString(app);
        } else {
            return XmlXStream.getInstance().toXML(app);
        }
    }

    /*
     * Generate pay load for the given key.
     */
    private Value generatePayload(Key key) {
        Stopwatch tracer = null;
        AbstractInstanceRegistry registry = PeerAwareInstanceRegistryImpl.getInstance();
        try {
            String payload;
            switch (key.getEntityType()) {
                case Application:
                    boolean isRemoteRegionRequested = key.hasRegions();

                    if (ALL_APPS.equals(key.getName())) {
                        if (isRemoteRegionRequested) {
                            tracer = serializeAllAppsWithRemoteRegionTimer.start();
                            payload = getPayLoad(key, registry.getApplicationsFromMultipleRegions(key.getRegions()));
                        } else {
                            tracer = serializeAllAppsTimer.start();
                            payload = getPayLoad(key, registry.getApplications());
                        }
                    } else if (ALL_APPS_DELTA.equals(key.getName())) {
                        if (isRemoteRegionRequested) {
                            tracer = serializeDeltaAppsWithRemoteRegionTimer.start();
                            versionDeltaWithRegions.incrementAndGet();
                            payload = getPayLoad(key,
                                    registry.getApplicationDeltasFromMultipleRegions(key.getRegions()));
                        } else {
                            tracer = serializeDeltaAppsTimer.start();
                            versionDelta.incrementAndGet();
                            payload = getPayLoad(key, registry.getApplicationDeltas());
                        }
                    } else {
                        tracer = serializeOneApptimer.start();
                        payload = getPayLoad(key, registry.getApplication(key.getName()));
                    }
                    break;
                case VIP:
                case SVIP:
                    tracer = serializeViptimer.start();
                    payload = getPayLoad(key, getApplicationsForVip(key, registry));
                    break;
                default:
                    logger.error("Unidentified entity type: " + key.getEntityType() + " found in the cache key.");
                    payload = "";
                    break;
            }
            return new Value(payload);
        } finally {
            if (tracer != null) {
                tracer.stop();
            }
        }
    }

    private static Applications getApplicationsForVip(Key key, AbstractInstanceRegistry registry) {
        Object[] args = {key.getEntityType(), key.getName(), key.getVersion(), key.getType()};
        logger.debug(
                "Retrieving applications from registry for key : {} {} {} {}",
                args);
        Applications toReturn = new Applications();
        Applications applications = registry.getApplications();
        for (Application application : applications.getRegisteredApplications()) {
            Application appToAdd = null;
            for (InstanceInfo instanceInfo : application.getInstances()) {
                String vipAddress;
                if (Key.EntityType.VIP.equals(key.getEntityType())) {
                    vipAddress = instanceInfo.getVIPAddress();
                } else if (Key.EntityType.SVIP.equals(key.getEntityType())) {
                    vipAddress = instanceInfo.getSecureVipAddress();
                } else {
                    // should not happen, but just in case.
                    continue;
                }

                if (null != vipAddress) {
                    String[] vipAddresses = vipAddress.split(",");
                    Arrays.sort(vipAddresses);
                    if (Arrays.binarySearch(vipAddresses, key.getName()) >= 0) {
                        if (null == appToAdd) {
                            appToAdd = new Application(application.getName());
                            toReturn.addApplication(appToAdd);
                        }
                        appToAdd.addInstance(instanceInfo);
                    }
                }
            }
        }
        toReturn.setAppsHashCode(toReturn.getReconcileHashCode());
        args = new Object[]{key.getEntityType(), key.getName(), key.getVersion(), key.getType(),
                toReturn.getReconcileHashCode()};
        logger.debug(
                "Retrieved applications from registry for key : {} {} {} {}, reconcile hashcode: {}",
                args);
        return toReturn;
    }

    /**
     * The key for the cached payload.
     */
    public static class Key {

        /**
         * An enum to define the entity that is stored in this cache for this key.
         */
        public enum EntityType {
            Application, VIP, SVIP
        }

        private final String entityName;
        private final String[] regions;
        private final KeyType requestType;
        private final Version requestVersion;
        private final String hashKey;
        private final EntityType entityType;

        public Key(EntityType entityType, String entityName, KeyType type, Version v) {
            this(entityType, entityName, null, type, v);
        }

        public Key(EntityType entityType, String entityName, @Nullable String[] regions, KeyType type, Version v) {
            this.regions = regions;
            this.entityType = entityType;
            this.entityName = entityName;
            requestType = type;
            requestVersion = v;
            hashKey = this.entityType + this.entityName + (null != this.regions ? Arrays.toString(this.regions) : "")
                    + requestType.name() + requestVersion.name();
        }

        public String getName() {
            return entityName;
        }

        public String getHashKey() {
            return hashKey;
        }

        public KeyType getType() {
            return requestType;
        }

        public Version getVersion() {
            return requestVersion;
        }

        public EntityType getEntityType() {
            return entityType;
        }

        public boolean hasRegions() {
            return null != regions && regions.length != 0;
        }

        public String[] getRegions() {
            return regions;
        }

        public Key cloneWithoutRegions() {
            return new Key(entityType, entityName, requestType, requestVersion);
        }

        @Override
        public int hashCode() {
            String hashKey = getHashKey();
            return hashKey.hashCode();
        }

        @Override
        public boolean equals(Object other) {
            if (other instanceof Key) {
                return getHashKey().equals(((Key) other).getHashKey());
            } else {
                return false;
            }
        }
    }

    /**
     * The class that stores payload in both compressed and uncompressed form.
     *
     */
    public class Value {
        private final String payload;
        private byte[] gzipped;

        public Value(String payload) {
            this.payload = payload;
            if (payload != EMPTY_PAYLOAD) {
                Stopwatch tracer = compressPayloadTimer.start();
                try {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    GZIPOutputStream out = new GZIPOutputStream(bos);
                    byte[] rawBytes = payload.getBytes();
                    out.write(rawBytes);
                    // Finish creation of gzip file
                    out.finish();
                    out.close();
                    bos.close();
                    gzipped = bos.toByteArray();
                } catch (IOException e) {
                    gzipped = null;
                } finally {
                    if (tracer != null) {
                        tracer.stop();
                    }
                }
            } else {
                gzipped = null;
            }
        }

        public String getPayload() {
            return payload;
        }

        public byte[] getGzipped() {
            return gzipped;
        }

    }

}
