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

import com.netflix.discovery.shared.resolver.ClusterResolver;
import com.netflix.discovery.shared.transport.EurekaHttpClient;
import com.netflix.discovery.shared.transport.EurekaHttpClientFactory;
import com.netflix.discovery.shared.transport.decorator.MetricsCollectingEurekaHttpClient;
import com.netflix.discovery.shared.transport.decorator.RebalancingEurekaHttpClient;
import com.netflix.discovery.shared.transport.decorator.RedirectingEurekaHttpClient;
import com.netflix.discovery.shared.transport.decorator.RetryableEurekaHttpClient;
import com.netflix.discovery.shared.transport.decorator.ServerStatusEvaluators;
import com.netflix.eureka.EurekaServerConfig;
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
                                                            ServerCodecs serverCodecs,
                                                            ClusterResolver clusterResolver) {
        EurekaHttpClientFactory jerseyFactory = new JerseyRemoteRegionClientFactory(serverConfig, serverCodecs, clusterResolver);
        EurekaHttpClientFactory metricsFactory = MetricsCollectingEurekaHttpClient.createFactory(jerseyFactory);

        RebalancingEurekaHttpClient client = new RebalancingEurekaHttpClient(
                RetryableEurekaHttpClient.createFactory(
                        clusterResolver,
                        RedirectingEurekaHttpClient.createFactory(metricsFactory),
                        ServerStatusEvaluators.legacyEvaluator()),
                RECONNECT_INTERVAL_MINUTES * 60 * 1000
        );

        return client;
    }
}
