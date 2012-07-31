/*
 * ClusteredNode.java
 *  
 * $Header: $ 
 * $DateTime: $
 *
 * Copyright (c) 2009 Netflix, Inc.  All rights reserved.
 */
package com.netflix.discovery.cluster;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.config.DynamicIntProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.discovery.CurrentRequestVersion;
import com.netflix.discovery.Version;
import com.netflix.discovery.resources.ASGResource.ASGStatus;
import com.netflix.discovery.shared.DiscoveryJerseyClient;
import com.netflix.discovery.shared.DiscoveryJerseyClient.JerseyClient;
import com.netflix.servo.DefaultMonitorRegistry;
import com.netflix.servo.annotations.DataSourceType;
import com.netflix.servo.monitor.Monitors;
import com.netflix.servo.monitor.Stopwatch;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.client.apache4.ApacheHttpClient4;

/**
 * Since discovery nodes comprise a cluster, a {@link ReplicaNode} represents 
 * node in the cluster that we need to replicate data to.
 * 
 * @author gkim
 */
public class ReplicaNode {
    
    private static final String NETFLIX_DISCOVERY_REPLICATE_STATUS_FOR_SURE = "netflix.discovery.replicateStatusForSure";
    private static final int RETRY_SLEEP_TIME_MS = 100;
    private static final int CONNECTION_WAIT_TIMEOUT = 200;
    private static final int MAX_TOTAL_CONNECTIONS = 1000;
    private static final int MAX_CONNECTIONS_PER_HOST = 500;
    private static final int READ_TIMEOUT = 200;
    private static final int CONNECT_TIMEOUT = 200;
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ReplicaNode.class); 

    
    public static final String HEADER_REPLICATION = "x-netflix-discovery-replication";
    //Tracer name constants
    private final static String PREFIX = "DiscoveryReplica: ";
    private final static String TRACER_REGISTER = PREFIX + "Register";
    private final static String TRACER_RENEW = PREFIX + "Renew";
    private final static String TRACER_CANCEL = PREFIX + "Cancel";
    private final static String TRACER_STATUS_UPDATE = PREFIX + "StatusUpdate";
    private final static String TRACER_ASG_STATUS_UPDATE = PREFIX + "ASGStatusUpdate";
     
    private final static String FMT_SERVICE_URL = "%sapps/%s";
    private final static String FMT_SERVICE_URL_ASG = "%sasg/%s";
    
    private final static DynamicIntProperty connectTimeout = DynamicPropertyFactory.getInstance().getIntProperty("replicanode.ConnectTimeout", 200);
    private final static DynamicIntProperty readTimeout = DynamicPropertyFactory.getInstance().getIntProperty("replicanode.ReadTimeout", 200);
    private final static DynamicIntProperty connectionManagerTimeout = DynamicPropertyFactory.getInstance().getIntProperty("replicanode.ConnectionManagerTimeout", 200);
    private final static DynamicIntProperty maxConnectionPerHost = DynamicPropertyFactory.getInstance().getIntProperty("replicanode.MaxHttpConnectionsPerHost", 500);
    private final static DynamicIntProperty maxTotalHttpConnections = DynamicPropertyFactory.getInstance().getIntProperty("replicanode.MaxTotalHttpConnections", 1000);
    
    private final String _serviceUrl;
    private final String _logPrefix;
    private JerseyClient jerseyClient;
    private ApacheHttpClient4 _client;
    private final Status _status;
    private ThreadPoolExecutor statusReplicationService;
   
    
    public ReplicaNode(String serviceUrl, int replicaIndex) {
        String restClientName = "RestClient_Replica_Node_" + replicaIndex;

        _serviceUrl = serviceUrl.intern();
        _status = new Status();
        _logPrefix = getClass().getSimpleName() + ": " + serviceUrl + "apps/: ";
        ThreadFactory threadFactory = new ThreadFactoryBuilder().setDaemon(false).setNameFormat("replication.pool" + _serviceUrl).build();
        
        statusReplicationService = new ThreadPoolExecutor(1, 1, 10, TimeUnit.MINUTES, new ArrayBlockingQueue<Runnable>(10000), threadFactory){};
            
       synchronized (this._serviceUrl) {
            
            if (_client == null) {
                try {
                    jerseyClient = DiscoveryJerseyClient.createJerseyClient(this.connectTimeout.get(), this.readTimeout.get(), this.connectionManagerTimeout.get(), this.maxConnectionPerHost.get(), this.maxTotalHttpConnections.get());
                } catch (Throwable e) {
                    throw new RuntimeException(
                            "Cannot create new Rest client for :"
                            + restClientName);
                }
            }
        }
        try {
            
            DefaultMonitorRegistry.getInstance().register(Monitors.newObjectMonitor(_serviceUrl, this));
          
         } catch (Throwable e) {
                 LOGGER.warn(
                     "Cannot register the JMX monitor for the InstanceRegistry :"
                            , e);
         }

    }

   
    
    /**
     * Replicate register action to this replica
     */
    public void register(InstanceInfo info, long clock) throws Exception {
        Stopwatch tracer = Monitors.newTimer(TRACER_REGISTER).start();
        String urlPath = "apps/" + info.getAppName();
        ClientResponse response = null;
        
        try{
            response = _client.resource(_serviceUrl).path(urlPath).header(HEADER_REPLICATION, "true").type(MediaType.APPLICATION_JSON_TYPE).post(ClientResponse.class, info);
            
        } finally {
            if(response != null){
                response.close();
            }
            if(tracer != null){
                tracer.stop();
            }
        }
    }
   
    /**
     * Replicate un-register action to this replica
     */
    public void cancel(String appName, String id, long clock) throws Exception{
        ClientResponse response = null;
        Stopwatch tracer = Monitors.newTimer(TRACER_CANCEL).start();
       
        try{
            String urlPath = "apps/" + appName +"/"+id;
            response = _client.resource(_serviceUrl).path(urlPath).header(HEADER_REPLICATION, "true").delete(ClientResponse.class);
            if(response.getStatus() == 404) {
                LOGGER.warn(_logPrefix + appName+"/"+id +" : delete: missing entry.");
            }
            
        }finally {
            if(response != null){
                response.close();
            }
            if(tracer != null){
                tracer.stop();
            }
        }
    }
    
    /**
     * Replicate heartbeat action to this replica.  Returns false if id wasn't
     * found
     */
    public boolean heartbeat(String appName, String id, long clock, InstanceInfo info, InstanceStatus overriddenStatus) throws Exception {
        ClientResponse response = null;
        Stopwatch tracer = Monitors.newTimer(TRACER_RENEW).start(); 
        try {
            String urlPath = "apps/" + appName +"/"+id;
            WebResource r = _client.resource(_serviceUrl).path(urlPath).queryParam("status", info.getStatus().toString()).queryParam("lastDirtyTimestamp", info.getLastDirtyTimestamp().toString());
            if (overriddenStatus != null) {
                r.queryParam("overriddenstatus", overriddenStatus.name());
            }
            r.header(HEADER_REPLICATION, "true");
            response = r.put(ClientResponse.class);
            
            if(response.getStatus() == 404){
                LOGGER.warn(_logPrefix + appName+"/"+id +" : heartbeat: missing entry.");
                return false;
            }
        }finally {
            if(response != null){
                response.close();
            }
            if(tracer != null){
                tracer.stop();
            }
        }
        return true;
    }
    
    /**
     * Replicate status update to this replica.  Returns false if id wasn't
     * found
     */
   public boolean statusUpdate(String asgName, 
            ASGStatus newStatus
            ) throws Exception {
        ClientResponse response = null;
        Stopwatch tracer = Monitors.newTimer(TRACER_ASG_STATUS_UPDATE).start(); 
        try {
            String urlPath = "asg/" + asgName + "/status";
            response = _client.resource(_serviceUrl).path(urlPath).queryParam("value", newStatus.name()).header(HEADER_REPLICATION, "true").put(ClientResponse.class);
          
            if(response.getStatus() != 200){
                LOGGER.error(_logPrefix + asgName +" : statusUpdate:  failed!");;
                return false;
            }
         tracer.stop();
         return true;
        }finally {
            if(response != null){
                response.close();
            }
            if(tracer != null){
                tracer.stop();
            }
        }
    }
    
    public boolean statusUpdate(final String appName, final String id,
            final InstanceStatus newStatus, final long clock) throws Throwable {
        statusReplicationService.execute(new Runnable() {

            @Override
            public void run() {
                CurrentRequestVersion.set(Version.V2);
                boolean success = false;
                while (!success) {
                    ClientResponse response = null;
                    Stopwatch tracer = Monitors.newTimer(TRACER_STATUS_UPDATE).start();
                    try {
                        String urlPath = "apps/" + appName +"/"+id;
                        response = _client.resource(_serviceUrl).path(urlPath).queryParam("value", newStatus.name()).header(HEADER_REPLICATION, "true").put(ClientResponse.class);
                        if (response.getStatus() != 200) {
                            LOGGER.error(_logPrefix + appName + "/" + id
                                    + " : statusUpdate:  failed!");
                        }
                        success = true;
                    } catch (Throwable e) {
                        LOGGER.error(_logPrefix + appName + "/" + id
                                + " : statusUpdate:  failed!", e);
                        try {
                            Thread.sleep(RETRY_SLEEP_TIME_MS);
                        } catch (InterruptedException e1) {

                        }
                        if (!DynamicPropertyFactory.getInstance().getBooleanProperty(NETFLIX_DISCOVERY_REPLICATE_STATUS_FOR_SURE, true).get()) {
                           success = true;
                        }

                    } finally {
                        if (response != null) {
                            response.close();
                        }
                        if (tracer != null) {
                            tracer.stop();
                        }
                    }

                }

            }

        });
        return true;
    }
    
    public String getServiceUrl() {
        return _serviceUrl;
    }
    
    
    
    public Status getStatus() {
        return _status;
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                + ((_serviceUrl == null) ? 0 : _serviceUrl.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ReplicaNode other = (ReplicaNode) obj;
        if (_serviceUrl == null) {
            if (other._serviceUrl != null)
                return false;
        } else if (!_serviceUrl.equals(other._serviceUrl))
            return false;
        return true;
    }

    
    public class Status {
        private volatile long _lastUpdated;
        private volatile boolean _isAvailable;
        private volatile Throwable _lastThrowable;
        
        Status(){
            _lastUpdated = System.currentTimeMillis();
            _isAvailable = true; //optimistic
        }
        
        public boolean isAvailable() {
            return _isAvailable;
        }
        
        public Throwable getError() {
            return _lastThrowable;
        }
        
        public long getLastUpdated() {
            return _lastUpdated;
        }
        
        public void setSuccess() {
            _isAvailable = true;
            _lastUpdated = System.currentTimeMillis();
        }
        
        public void setFailure(Throwable t) {
            _isAvailable = false;
            _lastThrowable = t;
            _lastUpdated = System.currentTimeMillis();
        }
        
    }

    @com.netflix.servo.annotations.Monitor(name="itemsInReplicationPipeline", type=DataSourceType.GAUGE)
    public long getNumOfItemsInReplicationPipeline() {
        return statusReplicationService.getQueue().size();
    }
    
    public void clearStatusQueue() {
       if (statusReplicationService.getQueue().size() > 0) {
           LOGGER.info("Clearing the internal status queue for {}", _serviceUrl);
           statusReplicationService.getQueue().clear();
       }
    }
    

}
