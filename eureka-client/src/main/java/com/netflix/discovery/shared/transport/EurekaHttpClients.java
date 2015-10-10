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

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.discovery.shared.Applications;
import com.netflix.discovery.shared.resolver.AsyncResolver;
import com.netflix.discovery.shared.resolver.ClosableResolver;
import com.netflix.discovery.shared.resolver.ClusterResolver;
import com.netflix.discovery.shared.resolver.EurekaEndpoint;
import com.netflix.discovery.shared.resolver.LegacyClusterResolver;
import com.netflix.discovery.shared.resolver.aws.ApplicationsResolver;
import com.netflix.discovery.shared.resolver.aws.AwsEndpoint;
import com.netflix.discovery.shared.resolver.aws.EurekaHttpResolver;
import com.netflix.discovery.shared.resolver.aws.ZoneAffinityClusterResolver;
import com.netflix.discovery.shared.transport.decorator.MetricsCollectingEurekaHttpClient;
import com.netflix.discovery.shared.transport.decorator.SessionedEurekaHttpClient;
import com.netflix.discovery.shared.transport.decorator.RedirectingEurekaHttpClient;
import com.netflix.discovery.shared.transport.decorator.RetryableEurekaHttpClient;
import com.netflix.discovery.shared.transport.decorator.ServerStatusEvaluators;
import com.netflix.discovery.shared.transport.decorator.SplitEurekaHttpClient;
import com.netflix.discovery.shared.transport.jersey.JerseyEurekaHttpClientFactory;

/**
 * @author Tomasz Bak
 */
public final class EurekaHttpClients {

    private EurekaHttpClients() {
    }

    /**
     * Standard client, with legacy server resolver.
     */
    public static EurekaHttpClientFactory createStandardClientFactory(EurekaClientConfig clientConfig,
                                                                      EurekaTransportConfig transportConfig,
                                                                      InstanceInfo myInstanceInfo,
                                                                      ApplicationsResolver.ApplicationsSource applicationsSource) {
        ClosableResolver resolver = createStandardClusterResolver(
                clientConfig, transportConfig, myInstanceInfo, applicationsSource);
        return createStandardClientFactory(clientConfig, transportConfig, myInstanceInfo, resolver);
    }

    /**
     * Standard client supports: registration/query connectivity split, connection re-balancing and retry.
     */
    static EurekaHttpClientFactory createStandardClientFactory(final EurekaClientConfig clientConfig,
                                                               final EurekaTransportConfig transportConfig,
                                                               final InstanceInfo myInstanceInfo,
                                                               final ClusterResolver<EurekaEndpoint> clusterResolver) {
        final TransportClientFactory jerseyFactory = JerseyEurekaHttpClientFactory.create(clientConfig, myInstanceInfo);
        final TransportClientFactory metricsFactory = MetricsCollectingEurekaHttpClient.createFactory(jerseyFactory);

        return new EurekaHttpClientFactory() {
            @Override
            public EurekaHttpClient newClient() {
                SessionedEurekaHttpClient registrationClient = new SessionedEurekaHttpClient(
                        RetryableEurekaHttpClient.createFactory(
                                clusterResolver,
                                RedirectingEurekaHttpClient.createFactory(metricsFactory),
                                ServerStatusEvaluators.legacyEvaluator()),
                        transportConfig.getSessionedClientReconnectIntervalSeconds() * 1000
                );
                SessionedEurekaHttpClient queryClient = new SessionedEurekaHttpClient(
                        RetryableEurekaHttpClient.createFactory(
                                clusterResolver,
                                RedirectingEurekaHttpClient.createFactory(metricsFactory),
                                ServerStatusEvaluators.legacyEvaluator()),
                        transportConfig.getSessionedClientReconnectIntervalSeconds() * 1000
                );

                return new SplitEurekaHttpClient(registrationClient, queryClient);
            }

            @Override
            public void shutdown() {
                wrapClosable(clusterResolver).shutdown();
                jerseyFactory.shutdown();
                metricsFactory.shutdown();
            }
        };
    }

    static ClosableResolver<AwsEndpoint> createStandardClusterResolver(final EurekaClientConfig clientConfig,
                                                                       final EurekaTransportConfig transportConfig,
                                                                       final InstanceInfo myInstanceInfo,
                                                                       final ApplicationsResolver.ApplicationsSource applicationsSource) {
        String[] availZones = clientConfig.getAvailabilityZones(clientConfig.getRegion());
        String myZone = InstanceInfo.getZone(availZones, myInstanceInfo);
        ClusterResolver bootstrapResolver = new LegacyClusterResolver(clientConfig, myZone);

        final EurekaHttpResolver remoteResolver = new EurekaHttpResolver(
                clientConfig,
                myInstanceInfo,
                bootstrapResolver,
                transportConfig.getReadClusterVip()
        );

        final ApplicationsResolver localResolver = new ApplicationsResolver(
                clientConfig,
                transportConfig,
                applicationsSource
        );

        return createStandardClusterResolver(remoteResolver, localResolver, clientConfig, transportConfig, myInstanceInfo);
    }

    /* testing */ static ClosableResolver<AwsEndpoint> createStandardClusterResolver(
            final EurekaHttpResolver remoteResolver,
            final ApplicationsResolver localResolver,
            final EurekaClientConfig clientConfig,
            final EurekaTransportConfig transportConfig,
            final InstanceInfo myInstanceInfo) {
        String[] availZones = clientConfig.getAvailabilityZones(clientConfig.getRegion());
        String myZone = InstanceInfo.getZone(availZones, myInstanceInfo);

        ClusterResolver<AwsEndpoint> compoundResolver = new ClusterResolver<AwsEndpoint>() {
            @Override
            public String getRegion() {
                return clientConfig.getRegion();
            }

            @Override
            public List<AwsEndpoint> getClusterEndpoints() {
                List<AwsEndpoint> result = localResolver.getClusterEndpoints();
                if (result.isEmpty()) {
                    result = remoteResolver.getClusterEndpoints();
                }

                return result;
            }
        };

        final AsyncResolver<AwsEndpoint> asyncResolver = new AsyncResolver<>(
                transportConfig,
                new ZoneAffinityClusterResolver(compoundResolver, myZone, true)
        );

        return new ClosableResolver<AwsEndpoint>() {
            @Override
            public void shutdown() {
                asyncResolver.shutdown();
                remoteResolver.shutdown();
            }

            @Override
            public String getRegion() {
                return asyncResolver.getRegion();
            }

            @Override
            public List<AwsEndpoint> getClusterEndpoints() {
                return asyncResolver.getClusterEndpoints();
            }
        };
    }


    static <T extends EurekaEndpoint> ClosableResolver<T> wrapClosable(final ClusterResolver<T> clusterResolver) {
        if (clusterResolver instanceof ClosableResolver) {
            return (ClosableResolver) clusterResolver;
        }

        return new ClosableResolver<T>() {
            @Override
            public void shutdown() {
                // no-op
            }

            @Override
            public String getRegion() {
                return clusterResolver.getRegion();
            }

            @Override
            public List<T> getClusterEndpoints() {
                return clusterResolver.getClusterEndpoints();
            }
        };
    }
}
