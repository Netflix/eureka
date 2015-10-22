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

import com.netflix.discovery.shared.dns.DnsServiceImpl;
import com.netflix.discovery.shared.resolver.ClusterResolver;
import com.netflix.discovery.shared.resolver.EurekaEndpoint;
import com.netflix.discovery.shared.transport.EurekaHttpClient;
import com.netflix.discovery.shared.transport.EurekaTransportConfig;
import com.netflix.discovery.shared.transport.TransportClientFactory;
import com.netflix.discovery.shared.transport.decorator.MetricsCollectingEurekaHttpClient;
import com.netflix.discovery.shared.transport.decorator.SessionedEurekaHttpClient;
import com.netflix.discovery.shared.transport.decorator.RedirectingEurekaHttpClient;
import com.netflix.discovery.shared.transport.decorator.RetryableEurekaHttpClient;
import com.netflix.discovery.shared.transport.decorator.ServerStatusEvaluators;
import com.netflix.eureka.EurekaServerConfig;
import com.netflix.eureka.Names;
import com.netflix.eureka.resources.ServerCodecs;

/**
 * @author Tomasz Bak
 */
public final class EurekaServerHttpClients {

    public static final long RECONNECT_INTERVAL_MINUTES = 30;

    private EurekaServerHttpClients() {
    }

    /**
     * {@link EurekaHttpClient} for remote region replication.
     */
    public static EurekaHttpClient createRemoteRegionClient(EurekaServerConfig serverConfig,
                                                            EurekaTransportConfig transportConfig,
                                                            ServerCodecs serverCodecs,
                                                            ClusterResolver<EurekaEndpoint> clusterResolver) {
        JerseyRemoteRegionClientFactory jerseyFactory = new JerseyRemoteRegionClientFactory(serverConfig, serverCodecs, clusterResolver.getRegion());
        TransportClientFactory metricsFactory = MetricsCollectingEurekaHttpClient.createFactory(jerseyFactory);

        SessionedEurekaHttpClient client = new SessionedEurekaHttpClient(
                Names.REMOTE,
                RetryableEurekaHttpClient.createFactory(
                        Names.REMOTE,
                        transportConfig,
                        clusterResolver,
                        createFactory(metricsFactory),
                        ServerStatusEvaluators.legacyEvaluator()),
                RECONNECT_INTERVAL_MINUTES * 60 * 1000
        );

        return client;
    }

    public static TransportClientFactory createFactory(final TransportClientFactory delegateFactory) {
        final DnsServiceImpl dnsService = new DnsServiceImpl();
        return new TransportClientFactory() {
            @Override
            public EurekaHttpClient newClient(EurekaEndpoint endpoint) {
                return new RedirectingEurekaHttpClient(endpoint.getServiceUrl(), delegateFactory, dnsService);
            }

            @Override
            public void shutdown() {
                delegateFactory.shutdown();
            }
        };
    }

}
