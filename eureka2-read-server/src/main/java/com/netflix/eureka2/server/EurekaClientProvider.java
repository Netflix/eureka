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

import javax.inject.Provider;
import javax.inject.Singleton;

import com.google.inject.Inject;
import com.netflix.eureka2.client.Eureka;
import com.netflix.eureka2.client.EurekaClient;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.client.resolver.ServerResolver.Server;
import com.netflix.eureka2.client.resolver.ServerResolvers;
import com.netflix.eureka2.metric.EurekaRegistryMetricFactory;
import com.netflix.eureka2.metric.client.EurekaClientMetricFactory;
import com.netflix.eureka2.server.config.EurekaCommonConfig;
import com.netflix.eureka2.server.config.EurekaCommonConfig.ResolverType;
import com.netflix.eureka2.server.config.EurekaCommonConfig.ServerBootstrap;
import com.netflix.eureka2.server.config.EurekaServerConfig;
import rx.functions.Func1;

/**
 * @author Tomasz Bak
 */
@Singleton
public class EurekaClientProvider implements Provider<EurekaClient> {

    private final EurekaServerConfig config;
    private final EurekaClientMetricFactory metricFactory;
    private final EurekaRegistryMetricFactory registryMetricFactory;

    @Inject
    public EurekaClientProvider(EurekaServerConfig config,
                                EurekaClientMetricFactory metricFactory,
                                EurekaRegistryMetricFactory registryMetricFactory) {
        this.config = config;
        this.metricFactory = metricFactory;
        this.registryMetricFactory = registryMetricFactory;
    }

    @Override
    public EurekaClient get() {
        ServerResolver discoveryResolver = createWriteServerResolver(
                new Func1<ServerBootstrap, Integer>() {
                    @Override
                    public Integer call(ServerBootstrap server) {
                        return server.getDiscoveryPort();
                    }
                });
        ServerResolver registrationResolver = createWriteServerResolver(
                new Func1<ServerBootstrap, Integer>() {
                    @Override
                    public Integer call(ServerBootstrap server) {
                        return server.getRegistrationPort();
                    }
                }
        );
        return Eureka.newClientBuilder(discoveryResolver, registrationResolver)
                .withCodec(config.getCodec())
                .withMetricFactory(metricFactory)
                .withMetricFactory(registryMetricFactory)
                .build();
    }

    // TODO: move to a more general place
    private ServerResolver createWriteServerResolver(Func1<ServerBootstrap, Integer> getPortFunc) {
        EurekaCommonConfig.ResolverType resolverType = config.getServerResolverType();
        if (resolverType == null) {
            throw new IllegalArgumentException("Write cluster resolver type not defined");
        }

        ServerResolver resolver;

        ServerBootstrap[] bootstraps = ServerBootstrap.from(config.getServerList());

        if (resolverType == ResolverType.dns) {
            resolver = forDNS(bootstraps, getPortFunc);
        } else {
            resolver = forFixed(bootstraps, getPortFunc);
        }
        return resolver;
    }

    private static ServerResolver forDNS(ServerBootstrap[] bootstraps, Func1<ServerBootstrap, Integer> getPortFunc) {
        if (bootstraps.length != 1) {
            throw new IllegalArgumentException("Expected one DNS name for server resolver, while got " + bootstraps.length);
        }
        return ServerResolvers.forDnsName(bootstraps[0].getHostname(), getPortFunc.call(bootstraps[0]));
    }

    private static ServerResolver forFixed(ServerBootstrap[] bootstraps, Func1<ServerBootstrap, Integer> getPortFunc) {
        Server[] servers = new Server[bootstraps.length];
        for (int i = 0; i < bootstraps.length; i++) {
            servers[i] = new Server(bootstraps[i].getHostname(), getPortFunc.call(bootstraps[i]));
        }
        return ServerResolvers.from(servers);
    }
}
