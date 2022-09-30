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

package com.netflix.discovery.shared.transport.jersey3;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.core.Feature;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import java.io.FileInputStream;
import java.io.IOException;
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
public class Jersey3ApplicationClientFactory implements TransportClientFactory {

    public static final String HTTP_X_DISCOVERY_ALLOW_REDIRECT = "X-Discovery-AllowRedirect";
    private static final String KEY_STORE_TYPE = "JKS";

    private final Client jersey3Client;
    private final MultivaluedMap<String, Object> additionalHeaders;

    public Jersey3ApplicationClientFactory(Client jersey3Client, MultivaluedMap<String, Object> additionalHeaders) {
        this.jersey3Client = jersey3Client;
        this.additionalHeaders = additionalHeaders;
    }

    @Override
    public EurekaHttpClient newClient(EurekaEndpoint endpoint) {
        return new Jersey3ApplicationClient(jersey3Client, endpoint.getServiceUrl(), additionalHeaders);
    }

    @Override
    public void shutdown() {
        jersey3Client.close();
    }
    
    public static Jersey3ApplicationClientFactory create(EurekaClientConfig clientConfig,
                                                         Collection<ClientRequestFilter> additionalFilters,
                                                         InstanceInfo myInstanceInfo,
                                                         AbstractEurekaIdentity clientIdentity) {
        return create(clientConfig, additionalFilters, myInstanceInfo, clientIdentity, Optional.empty(), Optional.empty());
    }
    
    public static Jersey3ApplicationClientFactory create(EurekaClientConfig clientConfig,
                                                         Collection<ClientRequestFilter> additionalFilters,
                                                         InstanceInfo myInstanceInfo,
                                                         AbstractEurekaIdentity clientIdentity,
                                                         Optional<SSLContext> sslContext,
                                                         Optional<HostnameVerifier> hostnameVerifier) {
        Jersey3ApplicationClientFactoryBuilder clientBuilder = newBuilder();
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

    public static Jersey3ApplicationClientFactoryBuilder newBuilder() {
        return new Jersey3ApplicationClientFactoryBuilder();
    }

    public static class Jersey3ApplicationClientFactoryBuilder extends EurekaClientFactoryBuilder<Jersey3ApplicationClientFactory, Jersey3ApplicationClientFactoryBuilder> {

        private List<Feature> features = new ArrayList<>();
        private List<ClientRequestFilter> additionalFilters = new ArrayList<>();

        public Jersey3ApplicationClientFactoryBuilder withFeature(Feature feature) {
            features.add(feature);
            return this;
        }

        Jersey3ApplicationClientFactoryBuilder withAdditionalFilters(Collection<ClientRequestFilter> additionalFilters) {
            if (additionalFilters != null) {
                this.additionalFilters.addAll(additionalFilters);
            }
            return this;
        }

        @Override
        public Jersey3ApplicationClientFactory build() {
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
            clientBuilder.register(new Jersey3EurekaIdentityHeaderFilter(identity));

            JerseyClient jerseyClient = (JerseyClient) clientBuilder.build();

            MultivaluedMap<String, Object> additionalHeaders = new MultivaluedHashMap<>();
            if (allowRedirect) {
                additionalHeaders.add(HTTP_X_DISCOVERY_ALLOW_REDIRECT, "true");
            }
            if (EurekaAccept.compact == eurekaAccept) {
                additionalHeaders.add(EurekaAccept.HTTP_X_EUREKA_ACCEPT, eurekaAccept.name());
            }

            return new Jersey3ApplicationClientFactory(jerseyClient, additionalHeaders);
        }

        private void addSSLConfiguration(ClientBuilder clientBuilder) {
            FileInputStream fin = null;
            try {
                if (systemSSL) {
                    clientBuilder.sslContext(SSLContext.getDefault());
                } else if (trustStoreFileName != null) {
                    KeyStore trustStore = KeyStore.getInstance(KEY_STORE_TYPE);
                    fin = new FileInputStream(trustStoreFileName);
                    trustStore.load(fin, trustStorePassword.toCharArray());
                    clientBuilder.trustStore(trustStore);
                } else if (sslContext != null) {
                    clientBuilder.sslContext(sslContext);
                }
            } catch (Exception ex) {
                throw new IllegalArgumentException("Cannot setup SSL for Jersey3 client", ex);
            }
            finally {
                if (fin != null) {
                    try {
                        fin.close();
                    } catch (IOException ignore) {
                    }
                }
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
