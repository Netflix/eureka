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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.discovery.endpoint.EndpointUtils;
import com.netflix.discovery.shared.resolver.AsyncResolver;
import com.netflix.discovery.shared.resolver.ClosableResolver;
import com.netflix.discovery.shared.resolver.ClusterResolver;
import com.netflix.discovery.shared.resolver.EurekaEndpoint;
import com.netflix.discovery.shared.resolver.StaticClusterResolver;
import com.netflix.discovery.shared.resolver.aws.ApplicationsResolver;
import com.netflix.discovery.shared.resolver.aws.AwsEndpoint;
import com.netflix.discovery.shared.resolver.aws.DnsTxtRecordClusterResolver;
import com.netflix.discovery.shared.resolver.aws.EurekaHttpResolver;
import com.netflix.discovery.shared.resolver.aws.ZoneAffinityClusterResolver;
import com.netflix.discovery.shared.transport.decorator.MetricsCollectingEurekaHttpClient;
import com.netflix.discovery.shared.transport.decorator.SessionedEurekaHttpClient;
import com.netflix.discovery.shared.transport.decorator.RedirectingEurekaHttpClient;
import com.netflix.discovery.shared.transport.decorator.RetryableEurekaHttpClient;
import com.netflix.discovery.shared.transport.decorator.ServerStatusEvaluators;
import com.netflix.discovery.shared.transport.jersey.JerseyEurekaHttpClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Tomasz Bak
 */
public final class EurekaHttpClients {
    private static final Logger logger = LoggerFactory.getLogger(EurekaHttpClients.class);

    private EurekaHttpClients() {
    }

    public static EurekaHttpClientFactory queryClientFactory(ClusterResolver bootstrapResolver,
                                                             TransportClientFactory transportClientFactory,
                                                             EurekaClientConfig clientConfig,
                                                             EurekaTransportConfig transportConfig,
                                                             InstanceInfo myInstanceInfo,
                                                             ApplicationsResolver.ApplicationsSource applicationsSource) {

        ClosableResolver queryResolver = transportConfig.useBootstrapResolverForQuery()
                ? wrapClosable(bootstrapResolver)
                : queryClientResolver(bootstrapResolver, transportClientFactory,
                clientConfig, transportConfig, myInstanceInfo, applicationsSource);
        return canonicalClientFactory(transportConfig, queryResolver, transportClientFactory);
    }

    public static EurekaHttpClientFactory registrationClientFactory(ClusterResolver bootstrapResolver,
                                                                    TransportClientFactory transportClientFactory,
                                                                    EurekaTransportConfig transportConfig) {
        return canonicalClientFactory(transportConfig, bootstrapResolver, transportClientFactory);
    }

    static EurekaHttpClientFactory canonicalClientFactory(final EurekaTransportConfig transportConfig,
                                                          final ClusterResolver<EurekaEndpoint> clusterResolver,
                                                          final TransportClientFactory transportClientFactory) {

        return new EurekaHttpClientFactory() {
            @Override
            public EurekaHttpClient newClient() {
                return new SessionedEurekaHttpClient(
                        RetryableEurekaHttpClient.createFactory(
                                clusterResolver,
                                RedirectingEurekaHttpClient.createFactory(transportClientFactory),
                                ServerStatusEvaluators.legacyEvaluator()),
                        transportConfig.getSessionedClientReconnectIntervalSeconds() * 1000
                );
            }

            @Override
            public void shutdown() {
                wrapClosable(clusterResolver).shutdown();
            }
        };
    }

    public static TransportClientFactory newTransportClientFactory(final EurekaClientConfig clientConfig,
                                                                   final InstanceInfo myInstanceInfo) {
        final TransportClientFactory jerseyFactory = JerseyEurekaHttpClientFactory.create(clientConfig, myInstanceInfo);
        final TransportClientFactory metricsFactory = MetricsCollectingEurekaHttpClient.createFactory(jerseyFactory);

        return new TransportClientFactory() {
            @Override
            public EurekaHttpClient newClient(EurekaEndpoint serviceUrl) {
                return metricsFactory.newClient(serviceUrl);
            }

            @Override
            public void shutdown() {
                metricsFactory.shutdown();
                jerseyFactory.shutdown();
            }
        };
    }

    // ==================================
    // Resolvers for the client factories
    // ==================================

    public static ClosableResolver<AwsEndpoint> newBootstrapResolver(final EurekaClientConfig clientConfig,
                                                                     final InstanceInfo myInstanceInfo) {
        String[] availZones = clientConfig.getAvailabilityZones(clientConfig.getRegion());
        String myZone = InstanceInfo.getZone(availZones, myInstanceInfo);

        ClusterResolver<AwsEndpoint> delegateResolver = new ZoneAffinityClusterResolver(
                defaultBootstrapResolver(clientConfig, myInstanceInfo),
                myZone,
                true
        );

        List<AwsEndpoint> initialValue = delegateResolver.getClusterEndpoints();

        return new AsyncResolver<>(
                "bootstrap",
                delegateResolver,
                initialValue,
                1,
                clientConfig.getEurekaServiceUrlPollIntervalSeconds() * 1000
        );
    }

    static ClusterResolver<AwsEndpoint> defaultBootstrapResolver(final EurekaClientConfig clientConfig,
                                                                 final InstanceInfo myInstanceInfo) {
        String[] availZones = clientConfig.getAvailabilityZones(clientConfig.getRegion());
        String myRegion = clientConfig.getRegion();
        String myZone = InstanceInfo.getZone(availZones, myInstanceInfo);

        ClusterResolver<AwsEndpoint> newResolver;
        if (clientConfig.shouldUseDnsForFetchingServiceUrls()) {
            String discoveryDnsName = "txt." + myRegion + '.' + clientConfig.getEurekaServerDNSName();
            newResolver = new DnsTxtRecordClusterResolver(
                    myRegion,
                    discoveryDnsName,
                    true,
                    Integer.parseInt(clientConfig.getEurekaServerPort()),
                    false,
                    clientConfig.getEurekaServerURLContext()
            );
        } else {
            Map<String, List<String>> serviceUrls = EndpointUtils.getServiceUrlsMapFromConfig(clientConfig, myZone, clientConfig.shouldPreferSameZoneEureka());
            List<AwsEndpoint> endpoints = new ArrayList<>(serviceUrls.size());
            for (String zone : serviceUrls.keySet()) {
               for(String url : serviceUrls.get(zone)) {
                   try {
                       URI serviceURI = new URI(url);
                       endpoints.add(new AwsEndpoint(
                               serviceURI.getHost(),
                               serviceURI.getPort(),
                               "https".equalsIgnoreCase(serviceURI.getSchemeSpecificPart()),
                               serviceURI.getPath(),
                               myRegion,
                               zone
                       ));
                   } catch (URISyntaxException ignore) {
                       logger.warn("Invalid eureka server URI: ; removing from the server pool", url);
                   }
               }
            }
            newResolver = new StaticClusterResolver<>(myRegion, endpoints);
        }

        return newResolver;
    }

    static ClosableResolver<AwsEndpoint> queryClientResolver(final ClusterResolver bootstrapResolver,
                                                             final TransportClientFactory transportClientFactory,
                                                             final EurekaClientConfig clientConfig,
                                                             final EurekaTransportConfig transportConfig,
                                                             final InstanceInfo myInstanceInfo,
                                                             final ApplicationsResolver.ApplicationsSource applicationsSource) {
        final EurekaHttpResolver remoteResolver = new EurekaHttpResolver(
                clientConfig,
                bootstrapResolver,
                transportClientFactory,
                transportConfig.getReadClusterVip()
        );

        final ApplicationsResolver localResolver = new ApplicationsResolver(
                clientConfig,
                transportConfig,
                applicationsSource
        );

        return queryClientResolver(remoteResolver, localResolver, clientConfig, transportConfig, myInstanceInfo);
    }


    /* testing */ static ClosableResolver<AwsEndpoint> queryClientResolver(
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
                "query",
                new ZoneAffinityClusterResolver(compoundResolver, myZone, true),
                transportConfig.getAsyncExecutorThreadPoolSize(),
                transportConfig.getAsyncResolverRefreshIntervalMs(),
                transportConfig.getAsyncResolverWarmUpTimeoutMs()
        );

        return new ClosableResolver<AwsEndpoint>() {
            @Override
            public void shutdown() {
                asyncResolver.shutdown();
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
