/*
 * ResponseCache.java
 *
 * $Header: //depot/webapplications/eureka/main/src/com/netflix/discovery/resources/ResponseCache.java#2 $
 * $DateTime: 2012/07/23 17:59:17 $
 *
 * Copyright (c) 2010 Netflix, Inc.  All rights reserved.
 */
package com.netflix.discovery.resources;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sun.font.X11TextRenderer.Tracer;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.netflix.discovery.CurrentRequestVersion;
import com.netflix.discovery.InstanceRegistry;
import com.netflix.discovery.ReplicaAwareInstanceRegistry;
import com.netflix.discovery.Version;
import com.netflix.discovery.converters.JsonXStream;
import com.netflix.discovery.converters.XmlXStream;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.servo.DefaultMonitorRegistry;
import com.netflix.servo.annotations.DataSourceType;
import com.netflix.servo.monitor.Monitors;
import com.netflix.servo.monitor.Stopwatch;

/**
 * Response cache is used for very popular queries such as the /v2/apps call.
 *
 * @author gkim
 */
public class ResponseCache {

    private static final int EXECUTION_INTERVAL_MS = 30*1000;

    private static Logger LOG = LoggerFactory.getLogger(ResponseCache.class); 

    public final static String ALL_APPS = "ALL_APPS";
    public final static String ALL_APPS_DELTA = "ALL_APPS_DELTA";

    public final static String TRACER_GET_ALL_APPS = "Discovery: ReponseCache: apply-all";
    public final static String TRACER_GET_ALL_APPS_DELTA = "Discovery: ReponseCache: apply-all-delta";
    
    public final static String TRACER_GET_APP = "Discovery: ReponseCache: apply-app";
    public final static String TRACER_GET_GZIP_PAYLOAD = "Discovery: ReponseCache: apply-app-gzip";
    private static final Timer timer = new Timer("ClientCacheFillTimer", true);
    private static final AtomicLong VERSION_DELTA = new AtomicLong(0);

    private final static String EMPTY_PAYLOAD = "";

    public enum KeyType { JSON, XML };

    public static class Key {
        private final String _app;
        private final KeyType _type;
        private final Version _version;
        private final String _hashKey;

        public Key(String app, KeyType type, Version v){
            _app = app;
            _type = type;
            _version = v;
            _hashKey = _app + _type.name() + _version.name();
        }

        public String getName() {
            return _app;
        }

        public String getHashKey() {
            return _hashKey;
        }

        public KeyType getType() {
            return _type;
        }

        public Version getVersion() {
            return _version;
        }

        @Override
        public int hashCode() {
            String hashKey = getHashKey();
            return hashKey.hashCode();
        }

        @Override
        public boolean equals(Object other) {
            if(other instanceof Key) {
                return getHashKey().equals( ((Key)other).getHashKey());
            }else {
                return false;
            }
        }
    }

    private static class Value {
        private final String payload;
        private byte[] gzipped;

        public Value(String payload) {
            this.payload = payload;
            if(payload != EMPTY_PAYLOAD){
                Stopwatch tracer = Monitors.newTimer(TRACER_GET_GZIP_PAYLOAD).start();
                try {
                    ByteArrayOutputStream bos =  new ByteArrayOutputStream();
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
                    if (tracer != null){
                        tracer.stop();
                    }
                }
            }
            else {
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

    private static final ResponseCache s_instance = new ResponseCache();

    private static final long CACHE_EXPIRATION_IN_SECONDS = 180;

    @com.netflix.servo.annotations.Monitor(name="responseCacheEntryRefresh", type=DataSourceType.COUNTER)
    private final AtomicLong _responseCacheEntryRefresh = new AtomicLong();

    @com.netflix.servo.annotations.Monitor(name="responseCacheRegistryRefresh", type=DataSourceType.COUNTER)
    private final AtomicLong _responseCacheRegistryRefresh = new AtomicLong();
    
    @com.netflix.servo.annotations.Monitor(name="responseCacheRegistryDeltaRefresh", type=DataSourceType.COUNTER)
    private final AtomicLong _responseCacheRegistryDeltaRefresh = new AtomicLong();

    private final ConcurrentMap<Key, Value> _readonlyCacheMap = new ConcurrentHashMap<Key, Value>();
    private final LoadingCache<Key, Value> _readwriteCacheMap = CacheBuilder.newBuilder()
    .initialCapacity(1000)
    .expireAfterWrite(CACHE_EXPIRATION_IN_SECONDS, TimeUnit.SECONDS)
    .build(
           new CacheLoader<Key, Value>() {
 
            @Override
            public Value load(Key key) throws Exception {
                return generatePayload(key);
            }
           });
        
       
    private ResponseCache() {
    	timer.schedule(new TimerTask() {

    	    @Override
    	    public void run() {
    	        try {
    	            updateClientCache();
    	        } catch (Throwable e) {
    	            LOG.error(
    	                    "Cannot update client cache from response cache: ",
    	                    e);
    	        }
    	    }
    	}, EXECUTION_INTERVAL_MS, EXECUTION_INTERVAL_MS);

    	  try {
              
              DefaultMonitorRegistry.getInstance().register(Monitors.newObjectMonitor("1", this));
            
           } catch (Throwable e) {
               LOG.warn(
                       "Cannot register the JMX monitor for the InstanceRegistry :"
                              , e);
           }
    }

    public static ResponseCache getInstance() { return s_instance; }

    @com.netflix.servo.annotations.Monitor(name="responseCacheSize", type=DataSourceType.GAUGE)
    public int getCurrentSize() {
        return _readwriteCacheMap.asMap().size();
    }

    public String get(final Key key){
        Value payload = getValue(key);

        //MapMaker doesn't like allowing nulls for values
        if(payload == null || payload.getPayload() == EMPTY_PAYLOAD){
            return null;
        }else {
            return payload.getPayload();
        }
    }

    private Value getValue(final Key key) {
        Value payload = null;
        try{
            final Value currentPayload = _readonlyCacheMap.get(key);
            if (currentPayload != null) {
                payload = currentPayload;
            }
            else {
                payload = _readwriteCacheMap.get(key);
                _readonlyCacheMap.put(key, payload);
            }
        }catch(Throwable t){
            LOG.error("Cannot get value for key :" + key, t);
        }
        return payload;
    }

    public byte[] getGZIP(Key key){
        Value payload = getValue(key);
        if (payload == null)
        	return null;
        return payload.getGzipped();
    }

    public void invalidate(String appName){
        for(KeyType type : KeyType.values()){
            for(Version v : Version.values()){
                invalidate(new Key(appName, type, v),
                        new Key(ALL_APPS, type, v),
                        new Key(ALL_APPS_DELTA, type, v));
            }
        }
    }

    public void invalidate(Key ...keys) {
        for(Key key : keys) {
            Object[] args = {key.getName(), key.getVersion(), key.getType()};
            LOG.debug("Invalidating the response cache key : {} {} {}", args);
            _readwriteCacheMap.invalidate(key);
        }
    }

    public static AtomicLong getVersionDelta() {
        return VERSION_DELTA;
    }

    private String getPayLoad(Key key, Applications apps){
        if(key.getType() == KeyType.JSON){
            return JsonXStream.getInstance().toXML(apps);
        }else {
            return XmlXStream.getInstance().toXML(apps);
        }
    }

    private String getPayLoad(Key key, Application app){
        if(app == null) {
            return EMPTY_PAYLOAD;
        }

        if(key.getType() == KeyType.JSON){
            return JsonXStream.getInstance().toXML(app);
        }else {
            return XmlXStream.getInstance().toXML(app);
        }
    }
    
    private void updateClientCache() {
        LOG.debug("Updating the client cache from response cache");
        for (ResponseCache.Key key : _readonlyCacheMap.keySet()) {
            Object[] args = {key.getName(), key.getVersion(), key.getType()};
            LOG.debug(
                    "Updating the client cache from response cache for key : %s %s %s", args
                    );
            try {
                CurrentRequestVersion.set(key.getVersion());
                Value cacheValue = _readwriteCacheMap.get(key);
                Value currentCacheValue = _readonlyCacheMap.get(key);
                if (cacheValue != currentCacheValue) {
                    _readonlyCacheMap.put(key, cacheValue);
                }
            } catch (Throwable th) {
                LOG.error(
                        "Error while updating the client cache from response cache",
                        th);
            }
        }
    }

    private Value generatePayload(Key key) {
        Stopwatch tracer = null;
        InstanceRegistry registry = ReplicaAwareInstanceRegistry.getInstance();
           try{
               String payload = null;
               if(ALL_APPS.equals(key.getName())){
                   tracer = Monitors.newTimer(TRACER_GET_ALL_APPS).start();
                   _responseCacheRegistryRefresh.incrementAndGet();
                   payload = getPayLoad(key, registry.getApplications());
               }
               else if (ALL_APPS_DELTA.equals(key.getName())) {
                   tracer = Monitors.newTimer(TRACER_GET_ALL_APPS_DELTA).start();
                   VERSION_DELTA.incrementAndGet();
                   _responseCacheRegistryDeltaRefresh.incrementAndGet();
                   payload = getPayLoad(key, registry.getApplicationDeltas());
               }
               else {
                   tracer = Monitors.newTimer(TRACER_GET_APP).start();
                   _responseCacheEntryRefresh.incrementAndGet();
                   payload = getPayLoad(key, registry.getApplication(key.getName()));
               }
               return new Value(payload);
           }finally {
               if (tracer != null){
                   tracer.stop();
               }
           }
    }
}