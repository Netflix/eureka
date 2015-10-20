/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.discovery.shared.transport.jersey;

import com.netflix.appinfo.AbstractEurekaIdentity;
import com.netflix.appinfo.EurekaClientIdentity;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.discovery.EurekaIdentityHeaderFilter;
import com.netflix.discovery.provider.DiscoveryJerseyProvider;
import com.netflix.discovery.shared.resolver.EurekaEndpoint;
import com.netflix.discovery.shared.transport.EurekaClientFactoryBuilder;
import com.netflix.discovery.shared.transport.EurekaHttpClient;
import com.netflix.discovery.shared.transport.TransportClientFactory;
import com.netflix.discovery.shared.transport.jersey.EurekaJerseyClientImpl.EurekaJerseyClientBuilder;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.filter.GZIPContentEncodingFilter;
import com.sun.jersey.client.apache4.ApacheHttpClient4;
import com.sun.jersey.client.apache4.config.ApacheHttpClient4Config;
import com.sun.jersey.client.apache4.config.DefaultApacheHttpClient4Config;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.scheme.SchemeSocketFactory;
import org.apache.http.conn.ssl.AllowAllHostnameVerifier;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.CoreProtocolPNames;

import static com.netflix.discovery.util.DiscoveryBuildInfo.buildVersion;

/**
 * @author Tomasz Bak
 */
public class JerseyEurekaHttpClientFactory implements TransportClientFactory {

    private final EurekaJerseyClient jerseyClient;
    private final ApacheHttpClient4 apacheClient;
    private final boolean allowRedirects;

    /**
     * @deprecated {@link EurekaJerseyClient} is deprecated and will be removed
     */
    public JerseyEurekaHttpClientFactory(EurekaJerseyClient jerseyClient, boolean allowRedirects) {
        this.jerseyClient = jerseyClient;
        this.apacheClient = jerseyClient.getClient();
        this.allowRedirects = allowRedirects;
    }

    public JerseyEurekaHttpClientFactory(ApacheHttpClient4 apacheClient, boolean allowRedirects) {
        this.jerseyClient = null;
        this.apacheClient = apacheClient;
        this.allowRedirects = allowRedirects;
    }

    @Override
    public EurekaHttpClient newClient(EurekaEndpoint endpoint) {
        return new JerseyApplicationClient(apacheClient, endpoint.getServiceUrl(), allowRedirects);
    }

    @Override
    public void shutdown() {
        if (jerseyClient != null) {
            jerseyClient.destroyResources();
        } else {
            apacheClient.destroy();
        }
    }

    public static JerseyEurekaHttpClientFactory create(EurekaClientConfig clientConfig,
                                                       InstanceInfo myInstanceInfo,
                                                       AbstractEurekaIdentity clientIdentity) {
        JerseyEurekaHttpClientFactoryBuilder clientBuilder = newBuilder()
                .withMyInstanceInfo(myInstanceInfo)
                .withUserAgent("Java-EurekaClient")
                .withAllowRedirect(clientConfig.allowRedirects())
                .withConnectionTimeout(clientConfig.getEurekaServerConnectTimeoutSeconds() * 1000)
                .withReadTimeout(clientConfig.getEurekaServerReadTimeoutSeconds() * 1000)
                .withMaxConnectionsPerHost(clientConfig.getEurekaServerTotalConnectionsPerHost())
                .withMaxTotalConnections(clientConfig.getEurekaServerTotalConnections())
                .withConnectionIdleTimeout(clientConfig.getEurekaConnectionIdleTimeoutSeconds())
                .withEncoder(clientConfig.getEncoderName())
                .withDecoder(clientConfig.getDecoderName(), clientConfig.getClientDataAccept())
                .withClientIdentity(clientIdentity);

        if ("true".equals(System.getProperty("com.netflix.eureka.shouldSSLConnectionsUseSystemSocketFactory"))) {
            clientBuilder.withClientName("DiscoveryClient-HTTPClient-System").withSystemSSLConfiguration();
        } else if (clientConfig.getProxyHost() != null && clientConfig.getProxyPort() != null) {
            clientBuilder.withClientName("Proxy-DiscoveryClient-HTTPClient")
                    .withProxy(
                            clientConfig.getProxyHost(), Integer.parseInt(clientConfig.getProxyPort()),
                            clientConfig.getProxyUserName(), clientConfig.getProxyPassword()
                    );
        } else {
            clientBuilder.withClientName("DiscoveryClient-HTTPClient");
        }

        return clientBuilder.build();
    }

    public static JerseyEurekaHttpClientFactory create(EurekaClientConfig clientConfig,
                                                       InstanceInfo myInstanceInfo) {
        return create(clientConfig, myInstanceInfo, new EurekaClientIdentity(myInstanceInfo.getIPAddr()));
    }

    public static JerseyEurekaHttpClientFactoryBuilder newBuilder() {
        return new JerseyEurekaHttpClientFactoryBuilder(false);
    }

    public static JerseyEurekaHttpClientFactoryBuilder experimentalBuilder() {
        return new JerseyEurekaHttpClientFactoryBuilder(true);
    }

    /**
     * Currently use EurekaJerseyClientBuilder. Once old transport in DiscoveryClient is removed, incorporate
     * EurekaJerseyClientBuilder here, and remove it.
     */
    public static class JerseyEurekaHttpClientFactoryBuilder extends EurekaClientFactoryBuilder<JerseyEurekaHttpClientFactory, JerseyEurekaHttpClientFactoryBuilder> {

        private final boolean experimental;

        public JerseyEurekaHttpClientFactoryBuilder(boolean experimental) {
            this.experimental = experimental;
        }

        @Override
        public JerseyEurekaHttpClientFactory build() {
            if (experimental) {
                return buildExperimental();
            }
            return buildLegacy();
        }

        private JerseyEurekaHttpClientFactory buildLegacy() {
            EurekaJerseyClientBuilder clientBuilder = new EurekaJerseyClientBuilder()
                    .withClientName(clientName)
                    .withUserAgent("Java-EurekaClient")
                    .withConnectionTimeout(connectionTimeout)
                    .withReadTimeout(readTimeout)
                    .withMaxConnectionsPerHost(maxConnectionsPerHost)
                    .withMaxTotalConnections(maxTotalConnections)
                    .withConnectionIdleTimeout(connectionIdleTimeout)
                    .withEncoderWrapper(encoderWrapper)
                    .withDecoderWrapper(decoderWrapper);

            EurekaJerseyClient jerseyClient = clientBuilder.build();
            ApacheHttpClient4 discoveryApacheClient = jerseyClient.getClient();
            addFilters(discoveryApacheClient);

            return new JerseyEurekaHttpClientFactory(jerseyClient, allowRedirect);
        }

        private JerseyEurekaHttpClientFactory buildExperimental() {
            ThreadSafeClientConnManager cm = createConnectionManager();
            ClientConfig clientConfig = new DefaultApacheHttpClient4Config();

            if (proxyHost != null) {
                addProxyConfiguration(clientConfig);
            }

            DiscoveryJerseyProvider discoveryJerseyProvider = new DiscoveryJerseyProvider(encoderWrapper, decoderWrapper);
            clientConfig.getSingletons().add(discoveryJerseyProvider);

            // Common properties to all clients
            cm.setDefaultMaxPerRoute(maxConnectionsPerHost);
            cm.setMaxTotal(maxTotalConnections);
            clientConfig.getProperties().put(ApacheHttpClient4Config.PROPERTY_CONNECTION_MANAGER, cm);

            String fullUserAgentName = (userAgent == null ? clientName : userAgent) + "/v" + buildVersion();
            clientConfig.getProperties().put(CoreProtocolPNames.USER_AGENT, fullUserAgentName);

            // To pin a client to specific server in case redirect happens, we handle redirects directly
            // (see DiscoveryClient.makeRemoteCall methods).
            clientConfig.getProperties().put(ClientConfig.PROPERTY_FOLLOW_REDIRECTS, Boolean.FALSE);
            clientConfig.getProperties().put(ClientPNames.HANDLE_REDIRECTS, Boolean.FALSE);

            ApacheHttpClient4 apacheClient = ApacheHttpClient4.create(clientConfig);
            addFilters(apacheClient);

            return new JerseyEurekaHttpClientFactory(apacheClient, allowRedirect);
        }

        /**
         * Since Jersey 1.19 depends on legacy apache http-client API, we have to as well.
         */
        private ThreadSafeClientConnManager createConnectionManager() {
            try {
                ThreadSafeClientConnManager connectionManager;
                if (sslContext != null) {
                    SchemeSocketFactory socketFactory = new SSLSocketFactory(sslContext, new AllowAllHostnameVerifier());
                    SchemeRegistry sslSchemeRegistry = new SchemeRegistry();
                    sslSchemeRegistry.register(new Scheme("https", 443, socketFactory));
                    connectionManager = new ThreadSafeClientConnManager(sslSchemeRegistry);
                } else {
                    connectionManager = new ThreadSafeClientConnManager();
                }
                return connectionManager;
            } catch (Exception e) {
                throw new IllegalStateException("Cannot initialize Apache connection manager", e);
            }
        }

        private void addProxyConfiguration(ClientConfig clientConfig) {
            if (proxyUserName != null && proxyPassword != null) {
                clientConfig.getProperties().put(ApacheHttpClient4Config.PROPERTY_PROXY_USERNAME, proxyUserName);
                clientConfig.getProperties().put(ApacheHttpClient4Config.PROPERTY_PROXY_PASSWORD, proxyPassword);
            } else {
                // Due to bug in apache client, user name/password must always be set.
                // Otherwise proxy configuration is ignored.
                clientConfig.getProperties().put(ApacheHttpClient4Config.PROPERTY_PROXY_USERNAME, "guest");
                clientConfig.getProperties().put(ApacheHttpClient4Config.PROPERTY_PROXY_PASSWORD, "guest");
            }
            clientConfig.getProperties().put(DefaultApacheHttpClient4Config.PROPERTY_PROXY_URI, "http://" + proxyHost + ':' + proxyPort);
        }

        private void addFilters(ApacheHttpClient4 discoveryApacheClient) {
            // Add gzip content encoding support
            discoveryApacheClient.addFilter(new GZIPContentEncodingFilter(false));

            // always enable client identity headers
            String ip = myInstanceInfo == null ? null : myInstanceInfo.getIPAddr();
            AbstractEurekaIdentity identity = clientIdentity == null ? new EurekaClientIdentity(ip) : clientIdentity;
            discoveryApacheClient.addFilter(new EurekaIdentityHeaderFilter(identity));
        }
    }
}