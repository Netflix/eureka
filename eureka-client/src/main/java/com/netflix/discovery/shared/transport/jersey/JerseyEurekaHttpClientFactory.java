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

import javax.inject.Inject;
import javax.inject.Singleton;

import com.netflix.discovery.EurekaClientConfig;
import com.netflix.discovery.shared.resolver.ClusterResolver;
import com.netflix.discovery.shared.transport.EurekaHttpClient;
import com.netflix.discovery.shared.transport.EurekaHttpClientFactory;
import com.netflix.discovery.shared.transport.jersey.EurekaJerseyClientImpl.EurekaJerseyClientBuilder;

/**
 * @author Tomasz Bak
 */
@Singleton
public class JerseyEurekaHttpClientFactory implements EurekaHttpClientFactory {

    private final EurekaClientConfig clientConfig;
    private final ClusterResolver clusterResolver;

    private volatile EurekaJerseyClient jerseyClient;
    private final Object lock = new Object();

    @Inject
    public JerseyEurekaHttpClientFactory(EurekaClientConfig clientConfig, ClusterResolver clusterResolver) {
        this.clientConfig = clientConfig;
        this.clusterResolver = clusterResolver;
    }

    @Override
    public EurekaHttpClient create(String... serviceUrl) {
        return new JerseyApplicationClient(getOrCreateJerseyClient(), serviceUrl[0], clientConfig.allowRedirects());
    }

    @Override
    public void shutdown() {
        if (jerseyClient != null) {
            jerseyClient.destroyResources();
        }
    }

    private EurekaJerseyClient getOrCreateJerseyClient() {
        if (jerseyClient != null) {
            return jerseyClient;
        }

        synchronized (lock) {
            if (jerseyClient == null) {
                if (clusterResolver.getClusterEndpoints().isEmpty()) {
                    throw new IllegalStateException("Eureka server list is empty; cannot setup connection to any server");
                }

                EurekaJerseyClientBuilder clientBuilder = new EurekaJerseyClientBuilder()
                        .withUserAgent("Java-EurekaClient")
                        .withConnectionTimeout(clientConfig.getEurekaServerConnectTimeoutSeconds() * 1000)
                        .withReadTimeout(clientConfig.getEurekaServerReadTimeoutSeconds() * 1000)
                        .withMaxConnectionsPerHost(clientConfig.getEurekaServerTotalConnectionsPerHost())
                        .withMaxTotalConnections(clientConfig.getEurekaServerTotalConnections())
                        .withConnectionIdleTimeout(clientConfig.getEurekaConnectionIdleTimeoutSeconds())
                        .withEncoder(clientConfig.getEncoderName())
                        .withDecoder(clientConfig.getDecoderName(), clientConfig.getClientDataAccept());

                if (clusterResolver.getClusterEndpoints().get(0).getServiceUrl().startsWith("https://") &&
                        "true".equals(System.getProperty("com.netflix.eureka.shouldSSLConnectionsUseSystemSocketFactory"))) {
                    clientBuilder.withClientName("DiscoveryClient-HTTPClient-System")
                            .withSystemSSLConfiguration();
                } else if (clientConfig.getProxyHost() != null && clientConfig.getProxyPort() != null) {
                    clientBuilder.withClientName("Proxy-DiscoveryClient-HTTPClient")
                            .withProxy(
                                    clientConfig.getProxyHost(), clientConfig.getProxyPort(),
                                    clientConfig.getProxyUserName(), clientConfig.getProxyPassword()
                            );
                } else {
                    clientBuilder.withClientName("DiscoveryClient-HTTPClient");
                }
                jerseyClient = clientBuilder.build();
            }
        }

        return jerseyClient;
    }
}