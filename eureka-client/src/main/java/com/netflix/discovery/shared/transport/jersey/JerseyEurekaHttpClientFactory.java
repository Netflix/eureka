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
import com.netflix.discovery.shared.resolver.EurekaEndpoint;
import com.netflix.discovery.shared.transport.EurekaHttpClient;
import com.netflix.discovery.shared.transport.TransportClientFactory;
import com.netflix.discovery.shared.transport.EurekaClientFactoryBuilder;
import com.netflix.discovery.shared.transport.jersey.EurekaJerseyClientImpl.EurekaJerseyClientBuilder;
import com.sun.jersey.api.client.filter.GZIPContentEncodingFilter;
import com.sun.jersey.client.apache4.ApacheHttpClient4;

/**
 * @author Tomasz Bak
 */
public class JerseyEurekaHttpClientFactory implements TransportClientFactory {

    private final EurekaJerseyClient jerseyClient;
    private final boolean allowRedirects;

    public JerseyEurekaHttpClientFactory(EurekaJerseyClient jerseyClient, boolean allowRedirects) {
        this.jerseyClient = jerseyClient;
        this.allowRedirects = allowRedirects;
    }

    public JerseyEurekaHttpClientFactory(JerseyEurekaHttpClientFactory delegate) {
        this(delegate.jerseyClient, delegate.allowRedirects);
    }

    @Override
    public EurekaHttpClient newClient(EurekaEndpoint endpoint) {
        return new JerseyApplicationClient(jerseyClient.getClient(), endpoint.getServiceUrl(), allowRedirects);
    }

    @Override
    public void shutdown() {
        if (jerseyClient != null) {
            jerseyClient.destroyResources();
        }
    }

    public static JerseyEurekaHttpClientFactory create(EurekaClientConfig clientConfig,
                                                 InstanceInfo myInstanceInfo,
                                                 boolean isSecure,
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

        if (isSecure && "true".equals(System.getProperty("com.netflix.eureka.shouldSSLConnectionsUseSystemSocketFactory"))) {
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
                                                 InstanceInfo myInstanceInfo,
                                                 boolean isSecure) {
        return create(clientConfig, myInstanceInfo, isSecure, new EurekaClientIdentity(myInstanceInfo.getIPAddr()));
    }

    public static JerseyEurekaHttpClientFactoryBuilder newBuilder() {
        return new JerseyEurekaHttpClientFactoryBuilder();
    }

    /**
     * Currently use EurekaJerseyClientBuilder. Once old transport in DiscoveryClient is removed, incorporate
     * EurekaJerseyClientBuilder here, and remove it.
     */
    public static class JerseyEurekaHttpClientFactoryBuilder extends EurekaClientFactoryBuilder<JerseyEurekaHttpClientFactory, JerseyEurekaHttpClientFactoryBuilder> {
        @Override
        public JerseyEurekaHttpClientFactory build() {
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

            // Add gzip content encoding support
            discoveryApacheClient.addFilter(new GZIPContentEncodingFilter(false));

            // always enable client identity headers
            String ip = myInstanceInfo == null ? null : myInstanceInfo.getIPAddr();
            AbstractEurekaIdentity identity = clientIdentity == null ? new EurekaClientIdentity(ip) : clientIdentity;
            discoveryApacheClient.addFilter(new EurekaIdentityHeaderFilter(identity));

            return new JerseyEurekaHttpClientFactory(jerseyClient, allowRedirect);
        }
    }
}