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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPOutputStream;

import com.netflix.appinfo.InstanceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.netflix.discovery.converters.JsonXStream;
import com.netflix.discovery.converters.XmlXStream;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.eureka.CurrentRequestVersion;
import com.netflix.eureka.EurekaServerConfig;
import com.netflix.eureka.EurekaServerConfigurationManager;
import com.netflix.eureka.InstanceRegistry;
import com.netflix.eureka.PeerAwareInstanceRegistry;
import com.netflix.eureka.Version;
import com.netflix.servo.annotations.DataSourceType;
import com.netflix.servo.monitor.Monitors;
import com.netflix.servo.monitor.Stopwatch;

import javax.annotation.Nullable;

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
 * formats and for multiple versions too. The cache is updated periodically to
 * reflect the latest information configured by
 * {@link EurekaServerConfig#getResponseCacheUpdateIntervalMs()}.
 * </p>
 *
 * @author Karthik Ranganathan, Greg Kim
 */
public class ResponseCache {

    private static final Logger logger = LoggerFactory
            .getLogger(ResponseCache.class);
    private static final EurekaServerConfig eurekaConfig = EurekaServerConfigurationManager
            .getInstance().getConfiguration();

    public final static String ALL_APPS = "ALL_APPS";
    public final static String ALL_APPS_DELTA = "ALL_APPS_DELTA";

    private final com.netflix.servo.monitor.Timer serializeAllAppsTimer = Monitors
            .newTimer("serialize-all");
    private final com.netflix.servo.monitor.Timer serializeDeltaAppsTimer = Monitors
            .newTimer("serialize-all-delta");
    private final com.netflix.servo.monitor.Timer serializeAllAppsWithRemoteRegionTimer = Monitors
            .newTimer("serialize-all_remote_region");
    private final com.netflix.servo.monitor.Timer serializeDeltaAppsWithRemoteRegionTimer = Monitors
            .newTimer("serialize-all-delta_remote_region");
    private final com.netflix.servo.monitor.Timer serializeOneApptimer = Monitors
            .newTimer("serialize-one");
    private final com.netflix.servo.monitor.Timer serializeViptimer = Monitors.newTimer("serialize-one-vip");
    private final com.netflix.servo.monitor.Timer compressPayloadTimer = Monitors
            .newTimer("compress-payload");

    private static final Timer timer = new Timer("Eureka -CacheFillTimer", true);
    private static final AtomicLong versionDelta = new AtomicLong(0);
    private static final AtomicLong versionDeltaWithRegions = new AtomicLong(0);
    private final static String EMPTY_PAYLOAD = "";

    public enum KeyType {
        JSON, XML
    };

    private final ConcurrentMap<Key, Value> readOnlyCacheMap = new ConcurrentHashMap<Key, Value>();
    private final LoadingCache<Key, Value> readWriteCacheMap = CacheBuilder
            .newBuilder()
            .initialCapacity(1000)
            .expireAfterWrite(
                    eurekaConfig.getResponseCacheAutoExpirationInSeconds(),
                    TimeUnit.SECONDS).build(new CacheLoader<Key, Value>() {

                @Override
                public Value load(Key key) throws Exception {
                    return generatePayload(key);
                }
            });

    private static final ResponseCache s_instance = new ResponseCache();

    private ResponseCache() {
        long responseCacheUpdateIntervalMs = eurekaConfig.getResponseCacheUpdateIntervalMs();
        timer.schedule(getCacheUpdateTask(),
                new Date(((System.currentTimeMillis()/responseCacheUpdateIntervalMs)*responseCacheUpdateIntervalMs) + responseCacheUpdateIntervalMs),
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
                try {
                    updateClientCache();
                } catch (Throwable e) {
                    logger.error(
                            "Cannot update client cache from response cache: ",
                            e);
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
        Value payload = getValue(key);
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
        Value payload = getValue(key);
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
            Object[] args = { key.getEntityType(), key.getName(), key.getVersion(), key.getType() };
            logger.debug("Invalidating the response cache key : {} {} {} {}", args);
            readWriteCacheMap.invalidate(key);
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
    @com.netflix.servo.annotations.Monitor(name = "responseCacheSize", type = DataSourceType.GAUGE)
    public int getCurrentSize() {
        return readWriteCacheMap.asMap().size();
    }

    /**
     * Get the payload in both compressed and uncompressed form.
     */
    private Value getValue(final Key key) {
        Value payload = null;
        try {
            final Value currentPayload = readOnlyCacheMap.get(key);
            if (currentPayload != null) {
                payload = currentPayload;
            } else {
                payload = readWriteCacheMap.get(key);
                readOnlyCacheMap.put(key, payload);
            }
        } catch (Throwable t) {
            logger.error("Cannot get value for key :" + key, t);
        }
        return payload;
    }

    /**
     * Generate pay load with both JSON and XML formats for all applications.
     */
    private String getPayLoad(Key key, Applications apps) {
        if (key.getType() == KeyType.JSON) {
            return JsonXStream.getInstance().toXML(apps);
        } else {
            return XmlXStream.getInstance().toXML(apps);
        }
    }

    /**
     * Generate pay load with both JSON and XML formats for a given application.
     */
    private String getPayLoad(Key key, Application app) {
        if (app == null) {
            return EMPTY_PAYLOAD;
        }

        if (key.getType() == KeyType.JSON) {
            return JsonXStream.getInstance().toXML(app);
        } else {
            return XmlXStream.getInstance().toXML(app);
        }
    }

    /**
     * Update the read only cache periodically to the latest pay load.
     */
    private void updateClientCache() {
        logger.debug("Updating the client cache from response cache");
        for (ResponseCache.Key key : readOnlyCacheMap.keySet()) {
            Object[] args = { key.getEntityType(), key.getName(), key.getVersion(), key.getType() };
            logger.debug(
                    "Updating the client cache from response cache for key : {} {} {} {}",
                    args);
            try {
                CurrentRequestVersion.set(key.getVersion());
                Value cacheValue = readWriteCacheMap.get(key);
                Value currentCacheValue = readOnlyCacheMap.get(key);
                if (cacheValue != currentCacheValue) {
                    readOnlyCacheMap.put(key, cacheValue);
                }
            } catch (Throwable th) {
                logger.error(
                        "Error while updating the client cache from response cache",
                        th);
            }
        }
    }

    /*
     * Generate pay load for the given key.
     */
    private Value generatePayload(Key key) {
        Stopwatch tracer = null;
        InstanceRegistry registry = PeerAwareInstanceRegistry.getInstance();
        try {
            String payload;
            switch (key.getEntityType()) {
                case Application:
                    boolean isRemoteRegionRequested = key.hasRegions();

                    if (ALL_APPS.equals(key.getName())) {
                        if (isRemoteRegionRequested) {
                            tracer = this.serializeAllAppsWithRemoteRegionTimer.start();
                            payload = getPayLoad(key, registry.getApplicationsFromMultipleRegions(key.getRegions()));
                        } else {
                            tracer = this.serializeAllAppsTimer.start();
                            payload = getPayLoad(key, registry.getApplications());
                        }
                    } else if (ALL_APPS_DELTA.equals(key.getName())) {
                        if (isRemoteRegionRequested) {
                            tracer = this.serializeDeltaAppsWithRemoteRegionTimer.start();
                            versionDeltaWithRegions.incrementAndGet();
                            payload = getPayLoad(key, registry.getApplicationDeltasFromMultipleRegions(key.getRegions()));
                        } else {
                            tracer = this.serializeDeltaAppsTimer.start();
                            versionDelta.incrementAndGet();
                            payload = getPayLoad(key, registry.getApplicationDeltas());
                        }
                    } else {
                        tracer = this.serializeOneApptimer.start();
                        payload = getPayLoad(key, registry.getApplication(key.getName()));
                    }
                    break;
                case VIP:
                case SVIP:
                    tracer = this.serializeViptimer.start();
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

    private Applications getApplicationsForVip(Key key, InstanceRegistry registry) {
        Object[] args = { key.getEntityType(), key.getName(), key.getVersion(), key.getType() };
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
        args = new Object[]{ key.getEntityType(), key.getName(), key.getVersion(), key.getType(), toReturn.getReconcileHashCode() };
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
        public enum EntityType {Application, VIP, SVIP}

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
            hashKey = this.entityType + this.entityName + ((null != this.regions) ? Arrays.toString(this.regions) : "") +
                      requestType.name() + requestVersion.name();
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
    private class Value {
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
                    this.gzipped = bos.toByteArray();
                } catch (IOException e) {
                    this.gzipped = null;
                } finally {
                    if (tracer != null) {
                        tracer.stop();
                    }
                }
            } else {
                this.gzipped = null;
            }
        }

        public String getPayload() {
            return this.payload;
        }

        public byte[] getGzipped() {
            return this.gzipped;
        }

    }

}