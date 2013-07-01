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

import java.io.FileInputStream;
import java.security.KeyStore;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
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

    /**
     * Creates a Jersey client with the given configuration parameters
     * 
     * @param connectionTimeout
     *            - The connection timeout of the connection
     * @param readTimeout
     *            - The read timeout of the connection
     * @param maxConnectionsPerHost
     *            - The maximum number of connections to a particular host
     * @param maxTotalConnections
     *            - The maximum number of total connections across all hosts
     * @param connectionIdleTimeout
     *            - The idle timeout after which the connections will be cleaned
     *            up
     * @return - The jersey client object encapsulating the connection
     */
    public static JerseyClient createJerseyClient(int connectionTimeout,
            int readTimeout, int maxConnectionsPerHost,
            int maxTotalConnections, int connectionIdleTimeout) {
        try {
            ClientConfig jerseyClientConfig = new CustomApacheHttpClientConfig(
                    maxConnectionsPerHost, maxTotalConnections);

            return new JerseyClient(connectionTimeout, readTimeout,
                    maxConnectionsPerHost, maxTotalConnections,
                    connectionIdleTimeout, jerseyClientConfig);
        } catch (Throwable e) {
            throw new RuntimeException("Cannot create Jersey client ", e);
        }
    }

    /**
     * Creates the SSL based Jersey client with the given configuration
     * parameters
     * 
     * @param connectionTimeout
     *            - The connection timeout of the connection
     * @param readTimeout
     *            - The read timeout of the connection
     * @param maxConnectionsPerHost
     *            - The maximum number of connections to a particular host
     * @param maxTotalConnections
     *            - The maximum number of total connections across all hosts
     * @param connectionIdleTimeout
     *            - The idle timeout after which the connections will be cleaned
     *            up
     * @param trustStoreFileName
     *            - The full path to the trust store file
     * @param trustStorePassword
     *            - The password of the trust store file
     * @return - The jersey client object encapsulating the connection
     */

    public static JerseyClient createSSLJerseyClient(int connectionTimeout,
            int readTimeout, int maxConnectionsPerHost,
            int maxTotalConnections, int connectionIdleTimeout,
            String trustStoreFileName, String trustStorePassword) {
        try {

            ClientConfig jerseyClientConfig = new SSLCustomApacheHttpClientConfig(
                    maxConnectionsPerHost, maxTotalConnections,
                    trustStoreFileName, trustStorePassword);

            return new JerseyClient(connectionTimeout, readTimeout,
                    maxConnectionsPerHost, maxTotalConnections,
                    connectionIdleTimeout, jerseyClientConfig);
        } catch (Throwable e) {
            throw new RuntimeException("Cannot create SSL Jersey client ", e);
        }
    }

    private static class CustomApacheHttpClientConfig extends
    DefaultApacheHttpClient4Config {

        public CustomApacheHttpClientConfig(int maxConnectionsPerHost,
                int maxTotalConnections) throws Throwable {
            ThreadSafeClientConnManager cm = new org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager();
            cm.setDefaultMaxPerRoute(maxConnectionsPerHost);
            cm.setMaxTotal(maxTotalConnections);
            getProperties().put(
                    DefaultApacheHttpClient4Config.PROPERTY_CONNECTION_MANAGER,
                    cm);

        }

    }

    private static class SSLCustomApacheHttpClientConfig extends
    DefaultApacheHttpClient4Config {
        private static final String PROTOCOL_SCHEME = "SSL";
        private static final int HTTPS_PORT = 443;
        private static final String PROTOCOL = "https";
        private static final String KEYSTORE_TYPE = "JKS";

        public SSLCustomApacheHttpClientConfig(int maxConnectionsPerHost,
                int maxTotalConnections, String trustStoreFileName,
                String trustStorePassword) throws Throwable {

            SSLContext sslContext = SSLContext.getInstance(PROTOCOL_SCHEME);
            TrustManagerFactory tmf = TrustManagerFactory
            .getInstance(TrustManagerFactory.getDefaultAlgorithm());
            KeyStore sslKeyStore = KeyStore.getInstance(KEYSTORE_TYPE);
            FileInputStream fin = null;
            try {
                fin = new FileInputStream(trustStoreFileName);
                sslKeyStore.load(fin, trustStorePassword.toCharArray());
                tmf.init(sslKeyStore);
                sslContext.init(null, createTrustManagers(sslKeyStore), null);
                SSLSocketFactory sslSocketFactory = new SSLSocketFactory(
                        sslContext);
                sslSocketFactory
                .setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
                SchemeRegistry sslSchemeRegistry = new SchemeRegistry();
                sslSchemeRegistry.register(new Scheme(PROTOCOL, HTTPS_PORT,
                        sslSocketFactory));

                ThreadSafeClientConnManager cm = new org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager(
                        sslSchemeRegistry);
                cm.setDefaultMaxPerRoute(maxConnectionsPerHost);
                cm.setMaxTotal(maxTotalConnections);
                getProperties()
                .put(DefaultApacheHttpClient4Config.PROPERTY_CONNECTION_MANAGER,
                        cm);
            } finally {
                if (fin != null) {
                    fin.close();
                }
            }

        }
    }

    private static TrustManager[] createTrustManagers(KeyStore trustStore) {
        TrustManagerFactory factory = null;
        try {
            factory = TrustManagerFactory.getInstance(TrustManagerFactory
                    .getDefaultAlgorithm());
            factory.init(trustStore);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }

        final TrustManager[] managers = factory.getTrustManagers();

        return managers;

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
                final int connectionIdleTimeout, ClientConfig clientConfig) {
            try {
                jerseyClientConfig = clientConfig;

                jerseyClientConfig.getClasses().add(
                        DiscoveryJerseyProvider.class);
                apacheHttpClient = ApacheHttpClient4.create(jerseyClientConfig);
                HttpParams params = apacheHttpClient.getClientHandler()
                .getHttpClient().getParams();

                HttpConnectionParams.setConnectionTimeout(params,
                        connectionTimeout);
                HttpConnectionParams.setSoTimeout(params, readTimeout);

                eurekaConnCleaner.schedule(
                        new TimerTask() {

                            @Override
                            public void run() {
                                try {
                                    apacheHttpClient
                                    .getClientHandler()
                                    .getHttpClient()
                                    .getConnectionManager()
                                    .closeIdleConnections(
                                            connectionIdleTimeout,
                                            TimeUnit.SECONDS);
                                } catch (Throwable e) {
                                    s_logger.error("Cannot clean connections",
                                            e);
                                }

                            }

                        }, HTTP_CONNECTION_CLEANER_INTERVAL_MS,
                        HTTP_CONNECTION_CLEANER_INTERVAL_MS);
            } catch (Throwable e) {
                throw new RuntimeException("Cannot create Jersey client", e);
            }

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
