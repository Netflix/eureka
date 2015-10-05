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

package com.netflix.discovery.shared.transport;

import java.util.List;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.discovery.shared.resolver.ClusterResolver;
import com.netflix.discovery.shared.resolver.EurekaEndpoint;
import com.netflix.discovery.shared.resolver.LegacyClusterResolver;
import com.netflix.discovery.shared.transport.decorator.MetricsCollectingEurekaHttpClient;
import com.netflix.discovery.shared.transport.decorator.RebalancingEurekaHttpClient;
import com.netflix.discovery.shared.transport.decorator.RedirectingEurekaHttpClient;
import com.netflix.discovery.shared.transport.decorator.RetryableEurekaHttpClient;
import com.netflix.discovery.shared.transport.decorator.ServerStatusEvaluators;
import com.netflix.discovery.shared.transport.decorator.SplitEurekaHttpClient;
import com.netflix.discovery.shared.transport.jersey.JerseyEurekaHttpClientFactory;

/**
 * @author Tomasz Bak
 */
public final class EurekaHttpClients {

    public static final long RECONNECT_INTERVAL_MINUTES = 30;

    private EurekaHttpClients() {
    }

    /**
     * Standard client, with legacy server resolver.
     */
    public static EurekaHttpClientFactory createStandardClientFactory(EurekaClientConfig clientConfig,
                                                                      ApplicationInfoManager applicationInfoManager) {
        String[] availZones = clientConfig.getAvailabilityZones(clientConfig.getRegion());
        String myZone = InstanceInfo.getZone(availZones, applicationInfoManager.getInfo());
        return createStandardClientFactory(clientConfig, applicationInfoManager, new LegacyClusterResolver(clientConfig, myZone));
    }

    /**
     * Standard client supports: registration/query connectivity split, connection re-balancing and retry.
     */
    public static EurekaHttpClientFactory createStandardClientFactory(EurekaClientConfig clientConfig,
                                                                      ApplicationInfoManager applicationInfoManager,
                                                                      ClusterResolver clusterResolver) {
        List<EurekaEndpoint> clusterEndpoints = clusterResolver.getClusterEndpoints();
        if (clusterEndpoints.isEmpty()) {
            throw new TransportException("Cluster server pool is empty");
        }
        boolean isSecure = clusterEndpoints.get(0).isSecure();
        final EurekaHttpClientFactory jerseyFactory = JerseyEurekaHttpClientFactory.create(clientConfig, applicationInfoManager, isSecure);
        final EurekaHttpClientFactory metricsFactory = MetricsCollectingEurekaHttpClient.createFactory(jerseyFactory);

        RebalancingEurekaHttpClient registrationClient = new RebalancingEurekaHttpClient(
                RetryableEurekaHttpClient.createFactory(
                        clusterResolver,
                        RedirectingEurekaHttpClient.createFactory(metricsFactory),
                        ServerStatusEvaluators.legacyEvaluator()),
                RECONNECT_INTERVAL_MINUTES * 60 * 1000
        );
        RebalancingEurekaHttpClient queryClient = new RebalancingEurekaHttpClient(
                RetryableEurekaHttpClient.createFactory(
                        clusterResolver,
                        RedirectingEurekaHttpClient.createFactory(metricsFactory),
                        ServerStatusEvaluators.legacyEvaluator()),
                RECONNECT_INTERVAL_MINUTES * 60 * 1000
        );

        final SplitEurekaHttpClient splitEurekaHttpClient = new SplitEurekaHttpClient(registrationClient, queryClient);
        return new EurekaHttpClientFactory() {
            @Override
            public EurekaHttpClient create(String... serviceUrls) {
                return splitEurekaHttpClient;
            }

            @Override
            public void shutdown() {
                jerseyFactory.shutdown();
                metricsFactory.shutdown();
            }
        };
    }
}
