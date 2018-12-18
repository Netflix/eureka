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

package com.netflix.discovery.shared.resolver;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import com.netflix.discovery.EurekaClientConfig;
import com.netflix.discovery.endpoint.EndpointUtils;
import com.netflix.discovery.shared.resolver.aws.AwsEndpoint;
import com.netflix.discovery.shared.resolver.aws.DnsTxtRecordClusterResolver;
import com.netflix.discovery.shared.resolver.aws.ZoneAffinityClusterResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @deprecated as of 2016-02-11. Will be deleted in an upcoming release.
 * See {@link com.netflix.discovery.shared.resolver.aws.ConfigClusterResolver} for replacement.
 *
 * Server resolver that mimics the behavior of the original implementation. Either DNS or server
 * list resolvers are instantiated, and they can be swapped in runtime because of the dynamic configuration
 * change.
 * <h3>Failures</h3>
 * If there is configuration change (from DNS to server list or reverse), and the new resolver cannot be instantiated,
 * it will be retried with exponential back-off (see {@link ReloadingClusterResolver}).
 *
 * @author Tomasz Bak
 */
@Deprecated
public class LegacyClusterResolver implements ClusterResolver<AwsEndpoint> {

    private static final Logger logger = LoggerFactory.getLogger(LegacyClusterResolver.class);

    private final ClusterResolver<AwsEndpoint> delegate;

    public LegacyClusterResolver(EurekaClientConfig clientConfig, String myZone) {
        this.delegate = new ReloadingClusterResolver<>(
                new LegacyClusterResolverFactory(clientConfig, myZone),
                clientConfig.getEurekaServiceUrlPollIntervalSeconds() * 1000
        );
    }

    @Override
    public String getRegion() {
        return delegate.getRegion();
    }

    @Override
    public List<AwsEndpoint> getClusterEndpoints() {
        return delegate.getClusterEndpoints();
    }

    static class LegacyClusterResolverFactory implements ClusterResolverFactory<AwsEndpoint> {

        private final EurekaClientConfig clientConfig;
        private final String myRegion;
        private final String myZone;

        LegacyClusterResolverFactory(EurekaClientConfig clientConfig, String myZone) {
            this.clientConfig = clientConfig;
            this.myRegion = clientConfig.getRegion();
            this.myZone = myZone;
        }

        @Override
        public ClusterResolver<AwsEndpoint> createClusterResolver() {
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
                newResolver = new ZoneAffinityClusterResolver(newResolver, myZone, clientConfig.shouldPreferSameZoneEureka());
            } else {
                // FIXME Not randomized in the EndpointUtils.getServiceUrlsFromConfig, and no zone info to do this here
                newResolver = new StaticClusterResolver<>(myRegion, createEurekaEndpointsFromConfig());
            }

            return newResolver;
        }

        private List<AwsEndpoint> createEurekaEndpointsFromConfig() {
            List<String> serviceUrls = EndpointUtils.getServiceUrlsFromConfig(clientConfig, myZone, clientConfig.shouldPreferSameZoneEureka());
            List<AwsEndpoint> endpoints = new ArrayList<>(serviceUrls.size());
            for (String serviceUrl : serviceUrls) {
                try {
                    URI serviceURI = new URI(serviceUrl);
                    endpoints.add(new AwsEndpoint(
                            serviceURI.getHost(),
                            serviceURI.getPort(),
                            "https".equalsIgnoreCase(serviceURI.getSchemeSpecificPart()),
                            serviceURI.getPath(),
                            myRegion,
                            myZone
                    ));
                } catch (URISyntaxException ignore) {
                    logger.warn("Invalid eureka server URI: {}; removing from the server pool", serviceUrl);
                }
            }
            return endpoints;
        }
    }
}
