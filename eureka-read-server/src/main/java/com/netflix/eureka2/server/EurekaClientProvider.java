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
import com.netflix.eureka2.server.config.EurekaBootstrapConfig;
import com.netflix.eureka2.server.config.ReadServerConfig;
import rx.functions.Func0;
import rx.functions.Func1;

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
        ServerResolver discoveryResolver = createWriteServerResolver(
                new Func1<EurekaBootstrapConfig.WriteServerBootstrap, Integer>() {
                    @Override
                    public Integer call(EurekaBootstrapConfig.WriteServerBootstrap server) {
                        return server.getDiscoveryPort();
                    }
                });
        ServerResolver registrationResolver = createWriteServerResolver(
                new Func1<EurekaBootstrapConfig.WriteServerBootstrap, Integer>() {
                    @Override
                    public Integer call(EurekaBootstrapConfig.WriteServerBootstrap server) {
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
    private ServerResolver createWriteServerResolver(Func1<EurekaBootstrapConfig.WriteServerBootstrap, Integer> getPortFunc) {
        ServerResolver resolver;

        if (config.getResolverType() == null) {
            throw new IllegalArgumentException("resolver type not configured");
        }

        switch (config.getResolverType()) {
            case "dns":
                EurekaBootstrapConfig.WriteServerBootstrap server = config.getWriteClusterServerDns();
                resolver = ServerResolvers.forDnsName(server.getHostname(), getPortFunc.call(server));
                break;
            case "inline":
                EurekaBootstrapConfig.WriteServerBootstrap[] serverBootstraps = config.getWriteClusterServersInline();
                ServerResolver.Server[] servers = new ServerResolver.Server[serverBootstraps.length];
                for (int i = 0; i < servers.length; i++) {
                    servers[i] = new ServerResolver.Server(
                            serverBootstraps[i].getHostname(),
                            getPortFunc.call(serverBootstraps[i])
                    );
                }
                resolver = ServerResolvers.from(servers);
                break;
            default:
                throw new IllegalArgumentException("Invalid resolver type: " + config.getResolverType());
        }
        return resolver;
    }
}
