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

package com.netflix.discovery.shared.transport.jersey2;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.Feature;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.netflix.appinfo.AbstractEurekaIdentity;
import com.netflix.appinfo.EurekaAccept;
import com.netflix.appinfo.EurekaClientIdentity;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.discovery.provider.DiscoveryJerseyProvider;
import com.netflix.discovery.shared.resolver.EurekaEndpoint;
import com.netflix.discovery.shared.transport.EurekaClientFactoryBuilder;
import com.netflix.discovery.shared.transport.EurekaHttpClient;
import com.netflix.discovery.shared.transport.TransportClientFactory;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.JerseyClient;
import org.glassfish.jersey.message.GZipEncoder;

import static com.netflix.discovery.util.DiscoveryBuildInfo.buildVersion;

/**
 * @author Tomasz Bak
 */
public class Jersey2ApplicationClientFactory implements TransportClientFactory {

    public static final String HTTP_X_DISCOVERY_ALLOW_REDIRECT = "X-Discovery-AllowRedirect";
    private static final String KEY_STORE_TYPE = "JKS";

    private final Client jersey2Client;
    private final MultivaluedMap<String, Object> additionalHeaders;

    public Jersey2ApplicationClientFactory(Client jersey2Client, MultivaluedMap<String, Object> additionalHeaders) {
        this.jersey2Client = jersey2Client;
        this.additionalHeaders = additionalHeaders;
    }

    @Override
    public EurekaHttpClient newClient(EurekaEndpoint endpoint) {
        return new Jersey2ApplicationClient(jersey2Client, endpoint.getServiceUrl(), additionalHeaders);
    }

    @Override
    public void shutdown() {
        jersey2Client.close();
    }
    
    public static Jersey2ApplicationClientFactory create(EurekaClientConfig clientConfig,
            Collection<ClientRequestFilter> additionalFilters,
            InstanceInfo myInstanceInfo,
            AbstractEurekaIdentity clientIdentity) {
        return create(clientConfig, additionalFilters, myInstanceInfo, clientIdentity, Optional.empty(), Optional.empty());
    }
    
    public static Jersey2ApplicationClientFactory create(EurekaClientConfig clientConfig,
            Collection<ClientRequestFilter> additionalFilters,
            InstanceInfo myInstanceInfo,
            AbstractEurekaIdentity clientIdentity,
            Optional<SSLContext> sslContext,
            Optional<HostnameVerifier> hostnameVerifier) {
        Jersey2ApplicationClientFactoryBuilder clientBuilder = newBuilder();
        clientBuilder.withAdditionalFilters(additionalFilters);
        clientBuilder.withMyInstanceInfo(myInstanceInfo);
        clientBuilder.withUserAgent("Java-EurekaClient");
        clientBuilder.withClientConfig(clientConfig);
        clientBuilder.withClientIdentity(clientIdentity);
        
        sslContext.ifPresent(clientBuilder::withSSLContext);
        hostnameVerifier.ifPresent(clientBuilder::withHostnameVerifier);
        
        if ("true".equals(System.getProperty("com.netflix.eureka.shouldSSLConnectionsUseSystemSocketFactory"))) {
            clientBuilder.withClientName("DiscoveryClient-HTTPClient-System").withSystemSSLConfiguration();
        } else if (clientConfig.getProxyHost() != null && clientConfig.getProxyPort() != null) {
            clientBuilder.withClientName("Proxy-DiscoveryClient-HTTPClient")
            .withProxy(
                    clientConfig.getProxyHost(), Integer.parseInt(clientConfig.getProxyPort()),
                    clientConfig.getProxyUserName(), clientConfig.getProxyPassword());
        } else {
            clientBuilder.withClientName("DiscoveryClient-HTTPClient");
        }
        
        return clientBuilder.build();
    }

    public static Jersey2ApplicationClientFactoryBuilder newBuilder() {
        return new Jersey2ApplicationClientFactoryBuilder();
    }

    public static class Jersey2ApplicationClientFactoryBuilder extends EurekaClientFactoryBuilder<Jersey2ApplicationClientFactory, Jersey2ApplicationClientFactoryBuilder> {

        private List<Feature> features = new ArrayList<>();
        private List<ClientRequestFilter> additionalFilters = new ArrayList<>();

        public Jersey2ApplicationClientFactoryBuilder withFeature(Feature feature) {
            features.add(feature);
            return this;
        }

        Jersey2ApplicationClientFactoryBuilder withAdditionalFilters(Collection<ClientRequestFilter> additionalFilters) {
            if (additionalFilters != null) {
                this.additionalFilters.addAll(additionalFilters);
            }
            return this;
        }

        @Override
        public Jersey2ApplicationClientFactory build() {
            ClientBuilder clientBuilder = ClientBuilder.newBuilder();
            ClientConfig clientConfig = new ClientConfig();
            
            for (ClientRequestFilter filter : additionalFilters) {
                clientConfig.register(filter);
            }

            for (Feature feature : features) {
                clientConfig.register(feature);
            }
            
            addProviders(clientConfig);
            addSSLConfiguration(clientBuilder);
            addProxyConfiguration(clientConfig);
            
            if (hostnameVerifier != null) {
                clientBuilder.hostnameVerifier(hostnameVerifier);
            }

            // Common properties to all clients
            final String fullUserAgentName = (userAgent == null ? clientName : userAgent) + "/v" + buildVersion();
            clientBuilder.register(new ClientRequestFilter() { // Can we do it better, without filter?
                @Override
                public void filter(ClientRequestContext requestContext) {
                    requestContext.getHeaders().put(HttpHeaders.USER_AGENT, Collections.<Object>singletonList(fullUserAgentName));
                }
            });
            clientConfig.property(ClientProperties.FOLLOW_REDIRECTS, allowRedirect);
            clientConfig.property(ClientProperties.READ_TIMEOUT, readTimeout);
            clientConfig.property(ClientProperties.CONNECT_TIMEOUT, connectionTimeout);

            clientBuilder.withConfig(clientConfig);

            // Add gzip content encoding support
            clientBuilder.register(new GZipEncoder());

            // always enable client identity headers
            String ip = myInstanceInfo == null ? null : myInstanceInfo.getIPAddr();
            final AbstractEurekaIdentity identity = clientIdentity == null ? new EurekaClientIdentity(ip) : clientIdentity;
            clientBuilder.register(new Jersey2EurekaIdentityHeaderFilter(identity));

            JerseyClient jersey2Client = (JerseyClient) clientBuilder.build();

            MultivaluedMap<String, Object> additionalHeaders = new MultivaluedHashMap<>();
            if (allowRedirect) {
                additionalHeaders.add(HTTP_X_DISCOVERY_ALLOW_REDIRECT, "true");
            }
            if (EurekaAccept.compact == eurekaAccept) {
                additionalHeaders.add(EurekaAccept.HTTP_X_EUREKA_ACCEPT, eurekaAccept.name());
            }

            return new Jersey2ApplicationClientFactory(jersey2Client, additionalHeaders);
        }

        private void addSSLConfiguration(ClientBuilder clientBuilder) {
            try {
                if (systemSSL) {
                    clientBuilder.sslContext(SSLContext.getDefault());
                } else if (trustStoreFileName != null) {
                    KeyStore trustStore = KeyStore.getInstance(KEY_STORE_TYPE);
                    FileInputStream fin = new FileInputStream(trustStoreFileName);
                    trustStore.load(fin, trustStorePassword.toCharArray());
                    clientBuilder.trustStore(trustStore);
                } else if (sslContext != null) {
                    clientBuilder.sslContext(sslContext);
                }
            } catch (Exception ex) {
                throw new IllegalArgumentException("Cannot setup SSL for Jersey2 client", ex);
            }
        }

        private void addProxyConfiguration(ClientConfig clientConfig) {
            if (proxyHost != null) {
                String proxyAddress = proxyHost;
                if (proxyPort > 0) {
                    proxyAddress += ':' + proxyPort;
                }
                clientConfig.property(ClientProperties.PROXY_URI, proxyAddress);
                if (proxyUserName != null) {
                    if (proxyPassword == null) {
                        throw new IllegalArgumentException("Proxy user name provided but not password");
                    }
                    clientConfig.property(ClientProperties.PROXY_USERNAME, proxyUserName);
                    clientConfig.property(ClientProperties.PROXY_PASSWORD, proxyPassword);
                }
            }
        }

        private void addProviders(ClientConfig clientConfig) {
            DiscoveryJerseyProvider discoveryJerseyProvider = new DiscoveryJerseyProvider(encoderWrapper, decoderWrapper);
            clientConfig.register(discoveryJerseyProvider);

            // Disable json autodiscovery, since json (de)serialization is provided by DiscoveryJerseyProvider
            clientConfig.property(ClientProperties.JSON_PROCESSING_FEATURE_DISABLE, Boolean.TRUE);
            clientConfig.property(ClientProperties.MOXY_JSON_FEATURE_DISABLE, Boolean.TRUE);
        }
    }
}
