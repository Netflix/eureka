package com.netflix.discovery.shared;

import com.google.common.base.Preconditions;
import com.netflix.discovery.provider.DiscoveryJerseyProvider;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.client.apache4.config.ApacheHttpClient4Config;
import com.sun.jersey.client.apache4.config.DefaultApacheHttpClient4Config;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.security.KeyStore;

/**
 * @author David Liu
 */
public abstract class JerseyClientConfigBuilder<T extends JerseyClientConfigBuilder<T>> {

    protected final DefaultApacheHttpClient4Config client4Config;
    protected String clientName;
    protected int maxTotalConnections;
    protected int maxConnectionsPerHost;
    protected DiscoveryJerseyProvider discoveryJerseyProvider;

    public JerseyClientConfigBuilder() {
        this.client4Config = new DefaultApacheHttpClient4Config();
    }

    public T withClientName(String clientName) {
        this.clientName = clientName;
        return self();
    }

    public T withMaxTotalConnections(int maxTotalConnections) {
        this.maxTotalConnections = maxTotalConnections;
        return self();
    }

    public T withMaxConnectionsPerHost(int maxConnectionsPerHost) {
        this.maxConnectionsPerHost = maxConnectionsPerHost;
        return self();
    }

    public T withDiscoveryJerseyProvider(DiscoveryJerseyProvider discoveryJerseyProvider) {
        this.discoveryJerseyProvider = discoveryJerseyProvider;
        return self();
    }

    public T withProperty(String key, Object value) {
        client4Config.getProperties().put(key, value);
        return self();
    }

    public DefaultApacheHttpClient4Config build() {
        Preconditions.checkNotNull(clientName, "Client name can not be null.");
        try {
            _build();
        } catch (Exception e) {
            throw new RuntimeException("Cannot create JerseyClient config", e);
        }

        if (discoveryJerseyProvider == null) {
            discoveryJerseyProvider = new DiscoveryJerseyProvider();
        }
        client4Config.getSingletons().add(discoveryJerseyProvider);

        return client4Config;
    }

    public abstract void _build() throws Exception;

    @SuppressWarnings("unchecked")
    protected T self() {
        return (T) this;
    }


    public static ClientConfigBuilder newClientConfigBuilder() {
        return new ClientConfigBuilder();
    }

    public static ProxyClientConfigBuilder newProxyClientConfigBuilder() {
        return new ProxyClientConfigBuilder();
    }

    public static SSLClientConfigBuilder newSSLClientConfigBuilder() {
        return new SSLClientConfigBuilder();
    }

    public static SystemSSLClientConfigBuilder newSystemSSLClientConfigBuilder() {
        return new SystemSSLClientConfigBuilder();
    }

    public static class ClientConfigBuilder extends JerseyClientConfigBuilder<ClientConfigBuilder> {
        @Override
        public void _build() throws Exception {

            MonitoredConnectionManager cm = new MonitoredConnectionManager(clientName);
            cm.setDefaultMaxPerRoute(maxConnectionsPerHost);
            cm.setMaxTotal(maxTotalConnections);
            withProperty(ApacheHttpClient4Config.PROPERTY_CONNECTION_MANAGER, cm);
            // To pin a client to specific server in case redirect happens, we handle redirects directly
            // (see DiscoveryClient.makeRemoteCall methods).
            withProperty(ClientConfig.PROPERTY_FOLLOW_REDIRECTS, Boolean.FALSE);
            withProperty(ClientPNames.HANDLE_REDIRECTS, Boolean.FALSE);
        }
    }

    public static class ProxyClientConfigBuilder extends JerseyClientConfigBuilder<ProxyClientConfigBuilder> {

        protected String proxyUserName;
        protected String proxyPassword;
        protected String proxyHost;
        protected String proxyPort;


        public ProxyClientConfigBuilder withProxyUserName(String proxyUserName) {
            this.proxyUserName = proxyUserName;
            return self();
        }

        public ProxyClientConfigBuilder withProxyPassword(String proxyPassword) {
            this.proxyPassword = proxyPassword;
            return self();
        }

        public ProxyClientConfigBuilder withProxyHost(String proxyHost) {
            this.proxyHost = proxyHost;
            return self();
        }

        public ProxyClientConfigBuilder withProxyPort(String proxyPort) {
            this.proxyPort = proxyPort;
            return self();
        }

        @Override
        public void _build() throws Exception {

            MonitoredConnectionManager cm = new MonitoredConnectionManager(clientName);
            cm.setDefaultMaxPerRoute(maxConnectionsPerHost);
            cm.setMaxTotal(maxTotalConnections);
            withProperty(ApacheHttpClient4Config.PROPERTY_CONNECTION_MANAGER, cm);
            // To pin a client to specific server in case redirect happens, we handle redirects directly
            // (see DiscoveryClient.makeRemoteCall methods).
            withProperty(ClientConfig.PROPERTY_FOLLOW_REDIRECTS, Boolean.FALSE);
            withProperty(ClientPNames.HANDLE_REDIRECTS, Boolean.FALSE);

            if (proxyUserName != null && proxyPassword != null) {
                withProperty(ApacheHttpClient4Config.PROPERTY_PROXY_USERNAME, proxyUserName);
                withProperty(ApacheHttpClient4Config.PROPERTY_PROXY_PASSWORD, proxyPassword);
            } else {
                // Due to bug in apache client, user name/password must always be set.
                // Otherwise proxy configuration is ignored.
                withProperty(ApacheHttpClient4Config.PROPERTY_PROXY_USERNAME, "guest");
                withProperty(ApacheHttpClient4Config.PROPERTY_PROXY_PASSWORD, "guest");
            }
            withProperty(
                    DefaultApacheHttpClient4Config.PROPERTY_PROXY_URI,
                    "http://" + proxyHost + ":" + proxyPort);
        }
    }


    public static class SSLClientConfigBuilder extends JerseyClientConfigBuilder<SSLClientConfigBuilder> {
        private static final String PROTOCOL_SCHEME = "SSL";
        private static final int HTTPS_PORT = 443;
        private static final String PROTOCOL = "https";
        private static final String KEYSTORE_TYPE = "JKS";

        protected String trustStoreFileName;
        protected String trustStorePassword;


        public SSLClientConfigBuilder withTrustStoreFileName(String trustStoreFileName) {
            this.trustStoreFileName = trustStoreFileName;
            return self();
        }

        public SSLClientConfigBuilder withTrustStorePassword(String trustStorePassword) {
            this.trustStorePassword = trustStorePassword;
            return self();
        }

        @Override
        public void _build() throws Exception {

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
                sslSchemeRegistry.register(new Scheme(PROTOCOL, HTTPS_PORT, sslSocketFactory));

                MonitoredConnectionManager cm = new MonitoredConnectionManager(clientName, sslSchemeRegistry);
                cm.setDefaultMaxPerRoute(maxConnectionsPerHost);
                cm.setMaxTotal(maxTotalConnections);
                // To pin a client to specific server in case redirect happens, we handle redirects directly
                // (see DiscoveryClient.makeRemoteCall methods).
                withProperty(ApacheHttpClient4Config.PROPERTY_CONNECTION_MANAGER, cm);
                withProperty(ClientConfig.PROPERTY_FOLLOW_REDIRECTS, Boolean.FALSE);
                withProperty(ClientPNames.HANDLE_REDIRECTS, Boolean.FALSE);
            } finally {
                if (fin != null) {
                    fin.close();
                }
            }
        }

        private static TrustManager[] createTrustManagers(KeyStore trustStore) {
            TrustManagerFactory factory;
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
    }


    public static class SystemSSLClientConfigBuilder extends JerseyClientConfigBuilder<SystemSSLClientConfigBuilder> {
        private static final int HTTPS_PORT = 443;
        private static final String PROTOCOL = "https";

        @Override
        public void _build() throws Exception {
            SSLSocketFactory sslSocketFactory = SSLSocketFactory.getSystemSocketFactory();
            SchemeRegistry sslSchemeRegistry = new SchemeRegistry();
            sslSchemeRegistry.register(new Scheme(PROTOCOL, HTTPS_PORT, sslSocketFactory));

            MonitoredConnectionManager cm = new MonitoredConnectionManager(clientName, sslSchemeRegistry);
            cm.setDefaultMaxPerRoute(maxConnectionsPerHost);
            cm.setMaxTotal(maxTotalConnections);
            withProperty(ApacheHttpClient4Config.PROPERTY_CONNECTION_MANAGER, cm);
            // To pin a client to specific server in case redirect happens, we handle redirects directly
            // (see DiscoveryClient.makeRemoteCall methods).
            withProperty(ClientConfig.PROPERTY_FOLLOW_REDIRECTS, Boolean.FALSE);
            withProperty(ClientPNames.HANDLE_REDIRECTS, Boolean.FALSE);
        }
    }
}

