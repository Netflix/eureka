/*
 * DiscoveryClient.java
 *
 * $Header: $
 * $DateTime: $
 *
 * Copyright (c) 2009 Netflix, Inc.  All rights reserved.
 */
package com.netflix.discovery.shared;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.discovery.DiscoveryClient;
import com.netflix.niws.NIWSProvider;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.client.apache4.ApacheHttpClient4;
import com.sun.jersey.client.apache4.config.DefaultApacheHttpClient4Config;


/**
 * {@link DiscoveryClient} handles all communication w/ the discovery service
 * including registration, heartbeats, and queries
 * 
 * @author kranganathan,gkim
 */
public class DiscoveryJerseyClient {


  
    public static JerseyClient createJerseyClient(int connectionTimeout, int readTimeout, int waitTimeout, int maxConnectionsPerHost, int maxTotalConnections) {
        return new JerseyClient(connectionTimeout, readTimeout, waitTimeout, maxConnectionsPerHost, maxTotalConnections);
    }
     
    
    private static class CustomApacheHttpClientConfig extends DefaultApacheHttpClient4Config {

       
        public CustomApacheHttpClientConfig(int maxConnectionsPerHost, int maxTotalConnections) {
            ThreadSafeClientConnManager cm = new org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager();
            cm.setDefaultMaxPerRoute(maxConnectionsPerHost);
            cm.setMaxTotal(maxTotalConnections);
            getProperties().put(DefaultApacheHttpClient4Config.PROPERTY_CONNECTION_MANAGER, cm);
            
        }
        }
    
    public static class JerseyClient {
        
        private ApacheHttpClient4 _client ;
        
        ClientConfig cc;
        
        private Timer _connCleaner = new Timer(
                "Discovery-ConnectionCleaner", true);
        private static final Logger s_logger = LoggerFactory.getLogger(JerseyClient.class);
        
        public ApacheHttpClient4 getClient() {
            return _client;
        }
        
        public ClientConfig getClientconfig() {
            return cc;
        }
        
        
        public JerseyClient (int connectionTimeout, int readTimeout, int waitTimeout, int maxConnectionsPerHost, int maxTotalConnections) {
           cc = new CustomApacheHttpClientConfig(maxConnectionsPerHost, maxTotalConnections);
            cc.getClasses().add(NIWSProvider.class);
       _client = ApacheHttpClient4.create(cc);
       HttpParams params = _client.getClientHandler().getHttpClient().getParams();
       
       HttpConnectionParams.setConnectionTimeout(params, connectionTimeout);
       HttpConnectionParams.setSoTimeout(params, readTimeout);
       //params.setIntParameter(ClientPNames.C, waitTimeout);
       params.setIntParameter("http.connection-manager.timeout", waitTimeout);
       //_client.getClientHandler().getHttpClient().getParams().setIntParameter(HttpClientParams.CONNECTION_MANAGER_TIMEOUT, waitTimeout);
      // _client.setConnectTimeout(connectionTimeout);
      // _client.setReadTimeout(readTimeout);
       
       _connCleaner.schedule(new TimerTask() {
       
           

        @Override
           public void run() {
               try {
                   _client.getClientHandler().getHttpClient().getConnectionManager().closeIdleConnections(30, TimeUnit.SECONDS);
               }
               catch (Throwable e) {
                   s_logger.error("Cannot clean connections", e);
               }
               
           }
           
       }, 30*1000, 30*1000);
    
        }
    }
 
}
