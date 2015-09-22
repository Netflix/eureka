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

import com.netflix.discovery.EurekaClientConfig;
import com.netflix.discovery.shared.resolver.ClusterResolver;
import com.netflix.discovery.shared.resolver.DnsServiceImpl;
import com.netflix.discovery.shared.transport.decorator.RebalancingEurekaHttpClient;
import com.netflix.discovery.shared.transport.decorator.RedirectingEurekaHttpClient;
import com.netflix.discovery.shared.transport.decorator.RetryableEurekaHttpClient;
import com.netflix.discovery.shared.transport.decorator.SplitEurekaHttpClient;
import com.netflix.discovery.shared.transport.jersey.JerseyEurekaHttpClientFactory;

/**
 * @author Tomasz Bak
 */
public final class EurekaHttpClients {

    public static final long RECONNECT_INTERVAL_MINUTES = 30;
    public static final int NUMBER_OF_RETRIES = 3;

    private EurekaHttpClients() {
    }

    /**
     * Standard client supports: registration/query connectivity split, connection re-balancing and retry.
     */
    public static EurekaHttpClient createStandardClient(EurekaClientConfig clientConfig, ClusterResolver clusterResolver) {
        RetryableEurekaHttpClient registrationClient = new RetryableEurekaHttpClient(
                clusterResolver,
                createRebalancingClientFactory(createRedirectingClientFactory(new JerseyEurekaHttpClientFactory(clientConfig, clusterResolver))),
                NUMBER_OF_RETRIES
        );
        RetryableEurekaHttpClient queryClient = new RetryableEurekaHttpClient(
                clusterResolver,
                createRebalancingClientFactory(createRedirectingClientFactory(new JerseyEurekaHttpClientFactory(clientConfig, clusterResolver))),
                NUMBER_OF_RETRIES
        );

        return new SplitEurekaHttpClient(registrationClient, queryClient);
    }

    private static EurekaHttpClientFactory createRedirectingClientFactory(final EurekaHttpClientFactory delegateFactory) {
        final DnsServiceImpl dnsService = new DnsServiceImpl();
        return new EurekaHttpClientFactory() {
            @Override
            public EurekaHttpClient create(String... serviceUrls) {
                return new RedirectingEurekaHttpClient(serviceUrls[0], delegateFactory, dnsService);
            }

            @Override
            public void shutdown() {
                delegateFactory.shutdown();
            }
        };
    }

    private static EurekaHttpClientFactory createRebalancingClientFactory(final EurekaHttpClientFactory delegateFactory) {
        return new EurekaHttpClientFactory() {
            @Override
            public EurekaHttpClient create(String... serviceUrls) {
                return new RebalancingEurekaHttpClient(delegateFactory, RECONNECT_INTERVAL_MINUTES * 60 * 1000);
            }

            @Override
            public void shutdown() {
                delegateFactory.shutdown();
            }
        };
    }
}
