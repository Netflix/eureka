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

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import com.netflix.servo.monitor.BasicCounter;
import com.netflix.servo.monitor.BasicTimer;
import com.netflix.servo.monitor.Counter;
import com.netflix.servo.monitor.MonitorConfig;
import com.netflix.servo.monitor.Monitors;
import com.netflix.servo.monitor.Stopwatch;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.client.apache4.ApacheHttpClient4;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A wrapper for Jersey Apache Client to set the necessary configurations.
 *
 * @author Karthik Ranganathan
 *
 */
public class JerseyClient {

    private static final int HTTP_CONNECTION_CLEANER_INTERVAL_MS = 30 * 1000;

    private ApacheHttpClient4 apacheHttpClient;

    ClientConfig jerseyClientConfig;

    private ScheduledExecutorService eurekaConnCleaner =
            Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {

                private final AtomicInteger threadNumber = new AtomicInteger(1);

                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r, "Eureka-JerseyClient-Conn-Cleaner" + threadNumber.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }
            });

    private static final Logger s_logger = LoggerFactory.getLogger(JerseyClient.class);

    public ApacheHttpClient4 getClient() {
        return apacheHttpClient;
    }

    public ClientConfig getClientconfig() {
        return jerseyClientConfig;
    }

    public JerseyClient(int connectionTimeout, int readTimeout, final int connectionIdleTimeout, ClientConfig clientConfig) {
        try {
            jerseyClientConfig = clientConfig;
//                jerseyClientConfig.getSingletons().add(new DiscoveryJerseyProvider());
            apacheHttpClient = ApacheHttpClient4.create(jerseyClientConfig);
            HttpParams params = apacheHttpClient.getClientHandler().getHttpClient().getParams();

            HttpConnectionParams.setConnectionTimeout(params, connectionTimeout);
            HttpConnectionParams.setSoTimeout(params, readTimeout);

            eurekaConnCleaner.scheduleWithFixedDelay(
                    new ConnectionCleanerTask(connectionIdleTimeout), HTTP_CONNECTION_CLEANER_INTERVAL_MS,
                    HTTP_CONNECTION_CLEANER_INTERVAL_MS,
                    TimeUnit.MILLISECONDS);
        } catch (Throwable e) {
            throw new RuntimeException("Cannot create Jersey client", e);
        }
    }

    /**
     * Clean up resources.
     */
    public void destroyResources() {
        if (eurekaConnCleaner != null) {
            eurekaConnCleaner.shutdown();
        }
        if (apacheHttpClient != null) {
            apacheHttpClient.destroy();
        }
    }

    private class ConnectionCleanerTask implements Runnable {

        private final int connectionIdleTimeout;
        private final BasicTimer executionTimeStats;
        private final Counter cleanupFailed;

        public ConnectionCleanerTask(int connectionIdleTimeout) {
            this.connectionIdleTimeout = connectionIdleTimeout;
            MonitorConfig.Builder monitorConfigBuilder = MonitorConfig.builder("Eureka-Connection-Cleaner-Time");
            executionTimeStats = new BasicTimer(monitorConfigBuilder.build());
            cleanupFailed = new BasicCounter(MonitorConfig.builder("Eureka-Connection-Cleaner-Failure").build());
            try {
                Monitors.registerObject(this);
            } catch (Exception e) {
                s_logger.error("Unable to register with servo.", e);
            }
        }

        @Override
        public void run() {
            Stopwatch start = executionTimeStats.start();
            try {
                apacheHttpClient
                        .getClientHandler()
                        .getHttpClient()
                        .getConnectionManager()
                        .closeIdleConnections(connectionIdleTimeout, TimeUnit.SECONDS);
            } catch (Throwable e) {
                s_logger.error("Cannot clean connections", e);
                cleanupFailed.increment();
            } finally {
                if (null != start) {
                    start.stop();
                }
            }

        }

    }
}
