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

package com.netflix.discovery.shared;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.discovery.provider.DiscoveryJerseyProvider;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.client.apache4.ApacheHttpClient4;
import com.sun.jersey.client.apache4.config.DefaultApacheHttpClient4Config;

/**
 * A wrapper for Jersey Apache Client to set the necessary configurations.
 * 
 * @author Karthik Ranganathan
 * 
 */
public class EurekaJerseyClient {

    public static JerseyClient createJerseyClient(int connectionTimeout,
            int readTimeout, int maxConnectionsPerHost,
            int maxTotalConnections, int connectionIdleTimeout) {
        return new JerseyClient(connectionTimeout, readTimeout,
                maxConnectionsPerHost, maxTotalConnections,
                connectionIdleTimeout);
    }

    private static class CustomApacheHttpClientConfig extends
            DefaultApacheHttpClient4Config {

        public CustomApacheHttpClientConfig(int maxConnectionsPerHost,
                int maxTotalConnections) {
            ThreadSafeClientConnManager cm = new org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager();
            cm.setDefaultMaxPerRoute(maxConnectionsPerHost);
            cm.setMaxTotal(maxTotalConnections);
            getProperties().put(
                    DefaultApacheHttpClient4Config.PROPERTY_CONNECTION_MANAGER,
                    cm);

        }
    }

    public static class JerseyClient {

        private static final int HTTP_CONNECTION_CLEANER_INTERVAL_MS = 30 * 1000;

        private ApacheHttpClient4 apacheHttpClient;

        ClientConfig jerseyClientConfig;

        private Timer eurekaConnCleaner = new Timer("Eureka-connectionCleaner",
                true);
        private static final Logger s_logger = LoggerFactory
                .getLogger(JerseyClient.class);

        public ApacheHttpClient4 getClient() {
            return apacheHttpClient;
        }

        public ClientConfig getClientconfig() {
            return jerseyClientConfig;
        }

        public JerseyClient(int connectionTimeout, int readTimeout,
                int maxConnectionsPerHost, int maxTotalConnections,
                final int connectionIdleTimeout) {
            jerseyClientConfig = new CustomApacheHttpClientConfig(maxConnectionsPerHost,
                    maxTotalConnections);
            jerseyClientConfig.getClasses().add(DiscoveryJerseyProvider.class);
            apacheHttpClient = ApacheHttpClient4.create(jerseyClientConfig);
            HttpParams params = apacheHttpClient.getClientHandler().getHttpClient()
                    .getParams();

            HttpConnectionParams
                    .setConnectionTimeout(params, connectionTimeout);
            HttpConnectionParams.setSoTimeout(params, readTimeout);
 
            eurekaConnCleaner.schedule(
                    new TimerTask() {

                        @Override
                        public void run() {
                            try {
                                apacheHttpClient.getClientHandler()
                                        .getHttpClient()
                                        .getConnectionManager()
                                        .closeIdleConnections(
                                                connectionIdleTimeout,
                                                TimeUnit.SECONDS);
                            } catch (Throwable e) {
                                s_logger.error("Cannot clean connections", e);
                            }

                        }

                    }, HTTP_CONNECTION_CLEANER_INTERVAL_MS,
                    HTTP_CONNECTION_CLEANER_INTERVAL_MS);

        }
        
        /**
         * Clean up resources.
         */
        public void destroyResources() {
            if (this.eurekaConnCleaner != null) {
                this.eurekaConnCleaner.cancel();
            }
            if (this.apacheHttpClient != null) {
                this.apacheHttpClient.destroy();
            }
        }

    }
    
    
}
