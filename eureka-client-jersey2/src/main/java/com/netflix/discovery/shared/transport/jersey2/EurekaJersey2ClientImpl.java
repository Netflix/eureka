package com.netflix.discovery.shared.transport.jersey2;

import static com.netflix.discovery.util.DiscoveryBuildInfo.buildVersion;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;

import org.apache.http.client.params.ClientPNames;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.params.CoreProtocolPNames;
import org.glassfish.jersey.apache.connector.ApacheClientProperties;
import org.glassfish.jersey.apache.connector.ApacheConnectorProvider;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.discovery.converters.wrappers.CodecWrappers;
import com.netflix.discovery.converters.wrappers.DecoderWrapper;
import com.netflix.discovery.converters.wrappers.EncoderWrapper;
import com.netflix.discovery.provider.DiscoveryJerseyProvider;
import com.netflix.servo.monitor.BasicCounter;
import com.netflix.servo.monitor.BasicTimer;
import com.netflix.servo.monitor.Counter;
import com.netflix.servo.monitor.MonitorConfig;
import com.netflix.servo.monitor.Monitors;
import com.netflix.servo.monitor.Stopwatch;

/**
 * @author Tomasz Bak
 */
public class EurekaJersey2ClientImpl implements EurekaJersey2Client {

    private static final Logger s_logger = LoggerFactory.getLogger(EurekaJersey2ClientImpl.class);

    private static final int HTTP_CONNECTION_CLEANER_INTERVAL_MS = 30 * 1000;

    private static final String PROTOCOL = "https";
    private static final String PROTOCOL_SCHEME = "SSL";
    private static final int HTTPS_PORT = 443;
    private static final String KEYSTORE_TYPE = "JKS";

    private final Client apacheHttpClient;
    
    private final ConnectionCleanerTask connectionCleanerTask;

    ClientConfig jerseyClientConfig;

    private final ScheduledExecutorService eurekaConnCleaner =
            Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {

                private final AtomicInteger threadNumber = new AtomicInteger(1);

                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r, "Eureka-Jersey2Client-Conn-Cleaner" + threadNumber.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }
            });

    public EurekaJersey2ClientImpl(
            int connectionTimeout,
            int readTimeout,
            final int connectionIdleTimeout,
            ClientConfig clientConfig) {

        try {
            jerseyClientConfig = clientConfig;
            jerseyClientConfig.register(DiscoveryJerseyProvider.class);
            jerseyClientConfig.connectorProvider(new ApacheConnectorProvider());
            jerseyClientConfig.property(ClientProperties.CONNECT_TIMEOUT, connectionTimeout);
            jerseyClientConfig.property(ClientProperties.READ_TIMEOUT, readTimeout);
            apacheHttpClient = ClientBuilder.newClient(jerseyClientConfig);
            connectionCleanerTask = new ConnectionCleanerTask(connectionIdleTimeout); 
            eurekaConnCleaner.scheduleWithFixedDelay(
                    connectionCleanerTask, HTTP_CONNECTION_CLEANER_INTERVAL_MS,
                    HTTP_CONNECTION_CLEANER_INTERVAL_MS,
                    TimeUnit.MILLISECONDS);
        } catch (Throwable e) {
            throw new RuntimeException("Cannot create Jersey2 client", e);
        }
    }

    @Override
    public Client getClient() {
        return apacheHttpClient;
    }

    /**
     * Clean up resources.
     */
    @Override
    public void destroyResources() {
        if (eurekaConnCleaner != null) {
            // Execute the connection cleaner one final time during shutdown
            eurekaConnCleaner.execute(connectionCleanerTask);
            eurekaConnCleaner.shutdown();
        }
        if (apacheHttpClient != null) {
            apacheHttpClient.close();
        }
    }

    public static class EurekaJersey2ClientBuilder {

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
        private EncoderWrapper encoderWrapper;
        private DecoderWrapper decoderWrapper;

        public EurekaJersey2ClientBuilder withClientName(String clientName) {
            this.clientName = clientName;
            return this;
        }

        public EurekaJersey2ClientBuilder withUserAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        public EurekaJersey2ClientBuilder withConnectionTimeout(int connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
            return this;
        }

        public EurekaJersey2ClientBuilder withReadTimeout(int readTimeout) {
            this.readTimeout = readTimeout;
            return this;
        }

        public EurekaJersey2ClientBuilder withConnectionIdleTimeout(int connectionIdleTimeout) {
            this.connectionIdleTimeout = connectionIdleTimeout;
            return this;
        }

        public EurekaJersey2ClientBuilder withMaxConnectionsPerHost(int maxConnectionsPerHost) {
            this.maxConnectionsPerHost = maxConnectionsPerHost;
            return this;
        }

        public EurekaJersey2ClientBuilder withMaxTotalConnections(int maxTotalConnections) {
            this.maxTotalConnections = maxTotalConnections;
            return this;
        }

        public EurekaJersey2ClientBuilder withProxy(String proxyHost, String proxyPort, String user, String password) {
            this.proxyHost = proxyHost;
            this.proxyPort = proxyPort;
            this.proxyUserName = user;
            this.proxyPassword = password;
            return this;
        }

        public EurekaJersey2ClientBuilder withSystemSSLConfiguration() {
            this.systemSSL = true;
            return this;
        }

        public EurekaJersey2ClientBuilder withTrustStoreFile(String trustStoreFileName, String trustStorePassword) {
            this.trustStoreFileName = trustStoreFileName;
            this.trustStorePassword = trustStorePassword;
            return this;
        }

        public EurekaJersey2ClientBuilder withEncoder(String encoderName) {
            return this.withEncoderWrapper(CodecWrappers.getEncoder(encoderName));
        }

        public EurekaJersey2ClientBuilder withEncoderWrapper(EncoderWrapper encoderWrapper) {
            this.encoderWrapper = encoderWrapper;
            return this;
        }

        public EurekaJersey2ClientBuilder withDecoder(String decoderName, String clientDataAccept) {
            return this.withDecoderWrapper(CodecWrappers.resolveDecoder(decoderName, clientDataAccept));
        }

        public EurekaJersey2ClientBuilder withDecoderWrapper(DecoderWrapper decoderWrapper) {
            this.decoderWrapper = decoderWrapper;
            return this;
        }

        public EurekaJersey2Client build() {
            MyDefaultApacheHttpClient4Config config = new MyDefaultApacheHttpClient4Config();
            try {
                return new EurekaJersey2ClientImpl(
                        connectionTimeout,
                        readTimeout,
                        connectionIdleTimeout,
                        config);
            } catch (Throwable e) {
                throw new RuntimeException("Cannot create Jersey client ", e);
            }
        }

        class MyDefaultApacheHttpClient4Config extends ClientConfig {
            MyDefaultApacheHttpClient4Config() {
                PoolingHttpClientConnectionManager cm;

                if (systemSSL) {
                    cm = createSystemSslCM();
                } else if (trustStoreFileName != null) {
                    cm = createCustomSslCM();
                } else {
                    cm = new PoolingHttpClientConnectionManager();
                }

                if (proxyHost != null) {
                    addProxyConfiguration();
                }

                DiscoveryJerseyProvider discoveryJerseyProvider = new DiscoveryJerseyProvider(encoderWrapper, decoderWrapper);
//                getSingletons().add(discoveryJerseyProvider);
                register(discoveryJerseyProvider);

                // Common properties to all clients
                cm.setDefaultMaxPerRoute(maxConnectionsPerHost);
                cm.setMaxTotal(maxTotalConnections);
                property(ApacheClientProperties.CONNECTION_MANAGER, cm);

                String fullUserAgentName = (userAgent == null ? clientName : userAgent) + "/v" + buildVersion();
                property(CoreProtocolPNames.USER_AGENT, fullUserAgentName);

                // To pin a client to specific server in case redirect happens, we handle redirects directly
                // (see DiscoveryClient.makeRemoteCall methods).
                property(ClientProperties.FOLLOW_REDIRECTS, Boolean.FALSE);
                property(ClientPNames.HANDLE_REDIRECTS, Boolean.FALSE);

            }

            private void addProxyConfiguration() {
                if (proxyUserName != null && proxyPassword != null) {
                    property(ClientProperties.PROXY_USERNAME, proxyUserName);
                    property(ClientProperties.PROXY_PASSWORD, proxyPassword);
                } else {
                    // Due to bug in apache client, user name/password must always be set.
                    // Otherwise proxy configuration is ignored.
                    property(ClientProperties.PROXY_USERNAME, "guest");
                    property(ClientProperties.PROXY_PASSWORD, "guest");
                }
                property(ClientProperties.PROXY_URI, "http://" + proxyHost + ":" + proxyPort);
            }

            private PoolingHttpClientConnectionManager createSystemSslCM() {
                ConnectionSocketFactory socketFactory = SSLConnectionSocketFactory.getSystemSocketFactory();

                Registry registry = RegistryBuilder.<ConnectionSocketFactory>create()
                        .register(PROTOCOL, socketFactory)
                        .build();

                return new PoolingHttpClientConnectionManager(registry);
            }

            private PoolingHttpClientConnectionManager createCustomSslCM() {
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

                    ConnectionSocketFactory socketFactory =
                            new SSLConnectionSocketFactory(sslContext, SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);

                    Registry registry = RegistryBuilder.<ConnectionSocketFactory>create()
                            .register(PROTOCOL, socketFactory)
                            .build();

                    return new PoolingHttpClientConnectionManager(registry);
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
                HttpClientConnectionManager cm = (HttpClientConnectionManager) apacheHttpClient
                        .getConfiguration()
                        .getProperty(ApacheClientProperties.CONNECTION_MANAGER);
                cm.closeIdleConnections(connectionIdleTimeout, TimeUnit.SECONDS);
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