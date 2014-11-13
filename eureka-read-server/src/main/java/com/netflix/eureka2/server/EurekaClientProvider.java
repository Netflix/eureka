/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.eureka2.server;

import com.google.inject.Inject;
import com.netflix.eureka2.client.Eureka;
import com.netflix.eureka2.client.EurekaClient;
import com.netflix.eureka2.client.metric.EurekaClientMetricFactory;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.client.resolver.ServerResolvers;

import javax.inject.Provider;
import javax.inject.Singleton;

/**
 * @author Tomasz Bak
 */
@Singleton
public class EurekaClientProvider implements Provider<EurekaClient> {

    private final ReadServerConfig config;
    private final EurekaClientMetricFactory metricFactory;

    @Inject
    public EurekaClientProvider(ReadServerConfig config, EurekaClientMetricFactory metricFactory) {
        this.config = config;
        this.metricFactory = metricFactory;
    }

    @Override
    public EurekaClient get() {
        ServerResolver discoveryResolver = createWriteServerResolver(config.getWriteClusterDiscoveryPort());
        ServerResolver registrationResolver = createWriteServerResolver(config.getWriteClusterRegistrationPort());
        return Eureka.newClientBuilder(discoveryResolver, registrationResolver)
                     .withCodec(config.getCodec())
                     .withMetricFactory(metricFactory)
                     .build();
    }

    private ServerResolver createWriteServerResolver(int port) {
        ServerResolver resolver;

        if (config.getResolverType() == null) {
            throw new IllegalArgumentException("resolver type not configured");
        }

        String[] writeClusterServers = config.getWriteClusterServers();
        switch (config.getResolverType()) {
            case "dns":
                String serverDnsName = writeClusterServers[0];
                resolver = ServerResolvers.forDnsName(serverDnsName, port);
                break;
            case "inline":
                ServerResolver.Server[] servers = new ServerResolver.Server[writeClusterServers.length];
                for (int i = 0; i < writeClusterServers.length; i++) {
                    String serverHostName = writeClusterServers[i];
                    servers[i] = new ServerResolver.Server(serverHostName, port);
                }
                resolver = ServerResolvers.from(servers);
                break;
            default:
                throw new IllegalArgumentException("Invalid resolver type: " + config.getResolverType());
        }
        return resolver;
    }
}
