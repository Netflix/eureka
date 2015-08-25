package com.netflix.discovery.shared;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.netflix.discovery.provider.DiscoveryJerseyProvider;
import com.netflix.servo.monitor.BasicCounter;
import com.netflix.servo.monitor.BasicTimer;
import com.netflix.servo.monitor.Counter;
import com.netflix.servo.monitor.MonitorConfig;
import com.netflix.servo.monitor.Monitors;
import com.netflix.servo.monitor.Stopwatch;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.client.apache4.ApacheHttpClient4;
import com.sun.jersey.client.apache4.config.ApacheHttpClient4Config;
import com.sun.jersey.client.apache4.config.DefaultApacheHttpClient4Config;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.netflix.discovery.util.DiscoveryBuildInfo.buildVersion;

/**
 * @author Tomasz Bak
 */
public class EurekaJerseyClient {

    private static final Logger s_logger = LoggerFactory.getLogger(EurekaJerseyClient.class);

    private static final int HTTP_CONNECTION_CLEANER_INTERVAL_MS = 30 * 1000;

    private static final String PROTOCOL = "https";
    private static final String PROTOCOL_SCHEME = "SSL";
    private static final int HTTPS_PORT = 443;
    private static final String KEYSTORE_TYPE = "JKS";

    private final ApacheHttpClient4 apacheHttpClient;

    ClientConfig jerseyClientConfig;

    private final ScheduledExecutorService eurekaConnCleaner =
            Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {

                private final AtomicInteger threadNumber = new AtomicInteger(1);

                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r, "Eureka-JerseyClient-Conn-Cleaner" + threadNumber.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }
            });

    public EurekaJerseyClient(int connectionTimeout, int readTimeout, final int connectionIdleTimeout,
                              ClientConfig clientConfig) {
        try {
            jerseyClientConfig = clientConfig;
            jerseyClientConfig.getClasses().add(DiscoveryJerseyProvider.class);
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

    public ApacheHttpClient4 getClient() {
        return apacheHttpClient;
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

    public static class EurekaJerseyClientBuilder {

        private boolean systemSSL;
        private String clientName;
        private int maxConnectionsPerHost;
        private int maxTotalConnections;
        private String trustStoreFileName;
        private String trustStorePassword;
        private String userAgent;
        private String proxyUserName;
        private String proxyPassword;
        private String proxyHost;
        private String proxyPort;
        private int connectionTimeout;
        private int readTimeout;
        private int connectionIdleTimeout;

        public EurekaJerseyClientBuilder withClientName(String clientName) {
            this.clientName = clientName;
            return this;
        }

        public EurekaJerseyClientBuilder withUserAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        public EurekaJerseyClientBuilder withConnectionTimeout(int connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
            return this;
        }

        public EurekaJerseyClientBuilder withReadTimeout(int readTimeout) {
            this.readTimeout = readTimeout;
            return this;
        }

        public EurekaJerseyClientBuilder withConnectionIdleTimeout(int connectionIdleTimeout) {
            this.connectionIdleTimeout = connectionIdleTimeout;
            return this;
        }

        public EurekaJerseyClientBuilder withMaxConnectionsPerHost(int maxConnectionsPerHost) {
            this.maxConnectionsPerHost = maxConnectionsPerHost;
            return this;
        }

        public EurekaJerseyClientBuilder withMaxTotalConnections(int maxTotalConnections) {
            this.maxTotalConnections = maxTotalConnections;
            return this;
        }

        public EurekaJerseyClientBuilder withProxy(String proxyHost, String proxyPort, String user, String password) {
            this.proxyHost = proxyHost;
            this.proxyPort = proxyPort;
            this.proxyUserName = user;
            this.proxyPassword = password;
            return this;
        }

        public EurekaJerseyClientBuilder withSystemSSLConfiguration() {
            this.systemSSL = true;
            return this;
        }

        public EurekaJerseyClientBuilder withTrustStoreFile(String trustStoreFileName, String trustStorePassword) {
            this.trustStoreFileName = trustStoreFileName;
            this.trustStorePassword = trustStorePassword;
            return this;
        }

        public EurekaJerseyClient build() {
            MyDefaultApacheHttpClient4Config config = new MyDefaultApacheHttpClient4Config();
            try {
                return new EurekaJerseyClient(connectionTimeout, readTimeout, connectionIdleTimeout, config);
            } catch (Throwable e) {
                throw new RuntimeException("Cannot create Jersey client ", e);
            }
        }

        class MyDefaultApacheHttpClient4Config extends DefaultApacheHttpClient4Config {
            MyDefaultApacheHttpClient4Config() {
                MonitoredConnectionManager cm;

                if (systemSSL) {
                    cm = createSystemSslCM();
                } else if (trustStoreFileName != null) {
                    cm = createCustomSslCM();
                } else {
                    cm = new MonitoredConnectionManager(clientName);
                }

                if (proxyHost != null) {
                    addProxyConfiguration(cm);
                }

                // Common properties to all clients
                cm.setDefaultMaxPerRoute(maxConnectionsPerHost);
                cm.setMaxTotal(maxTotalConnections);
                getProperties().put(ApacheHttpClient4Config.PROPERTY_CONNECTION_MANAGER, cm);

                String fullUserAgentName = (userAgent == null ? clientName : userAgent) + "/v" + buildVersion();
                getProperties().put(CoreProtocolPNames.USER_AGENT, fullUserAgentName);

                // To pin a client to specific server in case redirect happens, we handle redirects directly
                // (see DiscoveryClient.makeRemoteCall methods).
                getProperties().put(PROPERTY_FOLLOW_REDIRECTS, Boolean.FALSE);
                getProperties().put(ClientPNames.HANDLE_REDIRECTS, Boolean.FALSE);

            }

            private void addProxyConfiguration(MonitoredConnectionManager cm) {
                if (proxyUserName != null && proxyPassword != null) {
                    getProperties().put(ApacheHttpClient4Config.PROPERTY_PROXY_USERNAME, proxyUserName);
                    getProperties().put(ApacheHttpClient4Config.PROPERTY_PROXY_PASSWORD, proxyPassword);
                } else {
                    // Due to bug in apache client, user name/password must always be set.
                    // Otherwise proxy configuration is ignored.
                    getProperties().put(ApacheHttpClient4Config.PROPERTY_PROXY_USERNAME, "guest");
                    getProperties().put(ApacheHttpClient4Config.PROPERTY_PROXY_PASSWORD, "guest");
                }
                getProperties().put(DefaultApacheHttpClient4Config.PROPERTY_PROXY_URI, "http://" + proxyHost + ":" + proxyPort);
            }

            private MonitoredConnectionManager createSystemSslCM() {
                MonitoredConnectionManager cm;
                SSLSocketFactory sslSocketFactory = SSLSocketFactory.getSystemSocketFactory();
                SchemeRegistry sslSchemeRegistry = new SchemeRegistry();
                sslSchemeRegistry.register(new Scheme(PROTOCOL, HTTPS_PORT, sslSocketFactory));
                cm = new MonitoredConnectionManager(clientName, sslSchemeRegistry);
                return cm;
            }

            private MonitoredConnectionManager createCustomSslCM() {
                FileInputStream fin = null;
                try {
                    SSLContext sslContext = SSLContext.getInstance(PROTOCOL_SCHEME);
                    KeyStore sslKeyStore = KeyStore.getInstance(KEYSTORE_TYPE);

                    fin = new FileInputStream(trustStoreFileName);
                    sslKeyStore.load(fin, trustStorePassword.toCharArray());

                    TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                    factory.init(sslKeyStore);

                    TrustManager[] trustManagers = factory.getTrustManagers();

                    sslContext.init(null, trustManagers, null);
                    SSLSocketFactory sslSocketFactory = new SSLSocketFactory(sslContext);
                    sslSocketFactory.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
                    SchemeRegistry sslSchemeRegistry = new SchemeRegistry();
                    sslSchemeRegistry.register(new Scheme(PROTOCOL, HTTPS_PORT, sslSocketFactory));

                    return new MonitoredConnectionManager(clientName, sslSchemeRegistry);
                } catch (Exception ex) {
                    throw new IllegalStateException("SSL configuration issue", ex);
                } finally {
                    if (fin != null) {
                        try {
                            fin.close();
                        } catch (IOException ignore) {
                        }
                    }
                }
            }
        }
    }

    private class ConnectionCleanerTask implements Runnable {

        private final int connectionIdleTimeout;
        private final BasicTimer executionTimeStats;
        private final Counter cleanupFailed;

        private ConnectionCleanerTask(int connectionIdleTimeout) {
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
