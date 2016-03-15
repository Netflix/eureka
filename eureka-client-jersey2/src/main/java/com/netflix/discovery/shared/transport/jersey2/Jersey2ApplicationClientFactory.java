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

import javax.net.ssl.SSLContext;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.Feature;
import javax.ws.rs.core.HttpHeaders;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.netflix.discovery.provider.DiscoveryJerseyProvider;
import com.netflix.discovery.shared.resolver.EurekaEndpoint;
import com.netflix.discovery.shared.transport.EurekaClientFactoryBuilder;
import com.netflix.discovery.shared.transport.EurekaHttpClient;
import com.netflix.discovery.shared.transport.TransportClientFactory;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.JerseyClient;

import static com.netflix.discovery.util.DiscoveryBuildInfo.buildVersion;

/**
 * @author Tomasz Bak
 */
public class Jersey2ApplicationClientFactory implements TransportClientFactory {

    private static final String KEY_STORE_TYPE = "JKS";

    private final JerseyClient jersey2Client;
    private final boolean allowRedirect;

    public Jersey2ApplicationClientFactory(JerseyClient jersey2Client, boolean allowRedirect) {
        this.jersey2Client = jersey2Client;
        this.allowRedirect = allowRedirect;
    }

    @Override
    public EurekaHttpClient newClient(EurekaEndpoint endpoint) {
        return new Jersey2ApplicationClient(jersey2Client, endpoint.getServiceUrl(), allowRedirect);
    }

    @Override
    public void shutdown() {
        jersey2Client.close();
    }

    public static Jersey2ApplicationClientFactoryBuilder newBuilder() {
        return new Jersey2ApplicationClientFactoryBuilder();
    }

    public static class Jersey2ApplicationClientFactoryBuilder extends EurekaClientFactoryBuilder<Jersey2ApplicationClientFactory, Jersey2ApplicationClientFactoryBuilder> {

        private List<Feature> features = new ArrayList<>();

        public Jersey2ApplicationClientFactoryBuilder withFeature(Feature feature) {
            features.add(feature);
            return this;
        }

        @Override
        public Jersey2ApplicationClientFactory build() {
            ClientBuilder clientBuilder = ClientBuilder.newBuilder();
            ClientConfig clientConfig = new ClientConfig();

            for (Feature feature : features) {
                clientConfig.register(feature);
            }

            addProviders(clientConfig);
            addSSLConfiguration(clientBuilder);
            addProxyConfiguration(clientConfig);

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
            JerseyClient jersey2Client = (JerseyClient) clientBuilder.build();

            return new Jersey2ApplicationClientFactory(jersey2Client, this.allowRedirect);
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
        }
    }
}
