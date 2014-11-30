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
import com.netflix.eureka2.server.config.EurekaCommonConfig;
import com.netflix.eureka2.server.config.EurekaCommonConfig.ServerBootstrap;
import com.netflix.eureka2.server.config.EurekaServerConfig;
import rx.functions.Func1;

import javax.inject.Provider;
import javax.inject.Singleton;

/**
 * @author Tomasz Bak
 */
@Singleton
public class EurekaClientProvider implements Provider<EurekaClient> {

    private final EurekaServerConfig config;
    private final EurekaClientMetricFactory metricFactory;

    @Inject
    public EurekaClientProvider(EurekaServerConfig config, EurekaClientMetricFactory metricFactory) {
        this.config = config;
        this.metricFactory = metricFactory;
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
                     .build();
    }

    // TODO: move to a more general place
    private ServerResolver createWriteServerResolver(Func1<ServerBootstrap, Integer> getPortFunc) {
        EurekaCommonConfig.ResolverType resolverType = config.getServerResolverType();
        ServerBootstrap[] bootstraps = ServerBootstrap.from(config.getServerList());
        ServerResolver[] resolvers = new ServerResolver[bootstraps.length];

        for (int i = 0; i < resolvers.length; i++) {
            resolvers[i] = resolveForType(bootstraps[i], resolverType, getPortFunc);
        }

        return ServerResolvers.from(resolvers);
    }

    private ServerResolver resolveForType(
            ServerBootstrap bootstrap,
            EurekaCommonConfig.ResolverType resolverType,
            Func1<ServerBootstrap, Integer> getPortFunc
    ) {
        if (resolverType == null) {
            throw new IllegalArgumentException("Write cluster resolver type not defined");
        }

        ServerResolver resolver;
        switch (resolverType) {
            case dns:
                resolver = ServerResolvers.forDnsName(bootstrap.getHostname(), getPortFunc.call(bootstrap));
                break;
            case fixed:
                resolver = ServerResolvers.just(bootstrap.getHostname(), getPortFunc.call(bootstrap));
                break;
            default:
                throw new IllegalArgumentException("Unrecognized write cluster resolver");
        }
        return resolver;
    }
}
