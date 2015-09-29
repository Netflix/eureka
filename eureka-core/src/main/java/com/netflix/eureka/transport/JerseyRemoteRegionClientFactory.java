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

package com.netflix.eureka.transport;

import javax.inject.Inject;
import java.net.InetAddress;
import java.net.UnknownHostException;

import com.netflix.discovery.EurekaIdentityHeaderFilter;
import com.netflix.discovery.shared.resolver.ClusterResolver;
import com.netflix.discovery.shared.transport.EurekaHttpClient;
import com.netflix.discovery.shared.transport.EurekaHttpClientFactory;
import com.netflix.discovery.shared.transport.jersey.EurekaJerseyClient;
import com.netflix.discovery.shared.transport.jersey.EurekaJerseyClientImpl.EurekaJerseyClientBuilder;
import com.netflix.discovery.shared.transport.jersey.JerseyApplicationClient;
import com.netflix.eureka.EurekaServerConfig;
import com.netflix.eureka.EurekaServerIdentity;
import com.netflix.eureka.resources.ServerCodecs;
import com.sun.jersey.api.client.filter.GZIPContentEncodingFilter;
import com.sun.jersey.client.apache4.ApacheHttpClient4;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Tomasz Bak
 */
public class JerseyRemoteRegionClientFactory implements EurekaHttpClientFactory {

    private static final Logger logger = LoggerFactory.getLogger(JerseyRemoteRegionClientFactory.class);

    private final EurekaServerConfig serverConfig;
    private final ServerCodecs serverCodecs;
    private final ClusterResolver clusterResolver;

    private volatile EurekaJerseyClient jerseyClient;
    private final Object lock = new Object();

    @Inject
    public JerseyRemoteRegionClientFactory(EurekaServerConfig serverConfig,
                                           ServerCodecs serverCodecs,
                                           ClusterResolver clusterResolver) {
        this.serverConfig = serverConfig;
        this.serverCodecs = serverCodecs;
        this.clusterResolver = clusterResolver;
    }

    @Override
    public EurekaHttpClient create(String... serviceUrl) {
        return new JerseyApplicationClient(getOrCreateJerseyClient(), serviceUrl[0], false);
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
                        .withUserAgent("Java-EurekaClient-RemoteRegion")
                        .withEncoderWrapper(serverCodecs.getFullJsonCodec())
                        .withDecoderWrapper(serverCodecs.getFullJsonCodec())
                        .withConnectionTimeout(serverConfig.getRemoteRegionConnectTimeoutMs())
                        .withReadTimeout(serverConfig.getRemoteRegionReadTimeoutMs())
                        .withMaxConnectionsPerHost(serverConfig.getRemoteRegionTotalConnectionsPerHost())
                        .withMaxTotalConnections(serverConfig.getRemoteRegionTotalConnections())
                        .withConnectionIdleTimeout(serverConfig.getRemoteRegionConnectionIdleTimeoutSeconds());

                if (clusterResolver.getClusterEndpoints().get(0).isSecure()) {
                    clientBuilder.withClientName("Discovery-RemoteRegionClient-" + clusterResolver.getRegion());
                } else if ("true".equals(System.getProperty("com.netflix.eureka.shouldSSLConnectionsUseSystemSocketFactory"))) {
                    clientBuilder.withClientName("Discovery-RemoteRegionSystemSecureClient-" + clusterResolver.getRegion())
                            .withSystemSSLConfiguration();
                } else {
                    clientBuilder.withClientName("Discovery-RemoteRegionSecureClient-" + clusterResolver.getRegion())
                            .withTrustStoreFile(
                                    serverConfig.getRemoteRegionTrustStore(),
                                    serverConfig.getRemoteRegionTrustStorePassword()
                            );
                }
                jerseyClient = clientBuilder.build();
                ApacheHttpClient4 discoveryApacheClient = jerseyClient.getClient();

                // Add gzip content encoding support
                boolean enableGZIPContentEncodingFilter = serverConfig.shouldGZipContentFromRemoteRegion();
                if (enableGZIPContentEncodingFilter) {
                    discoveryApacheClient.addFilter(new GZIPContentEncodingFilter(false));
                }

                // always enable client identity headers
                String ip = null;
                try {
                    ip = InetAddress.getLocalHost().getHostAddress();
                } catch (UnknownHostException e) {
                    logger.warn("Cannot find localhost ip", e);
                }
                EurekaServerIdentity identity = new EurekaServerIdentity(ip);
                discoveryApacheClient.addFilter(new EurekaIdentityHeaderFilter(identity));
            }
        }

        return jerseyClient;
    }
}
