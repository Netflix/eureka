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

package com.netflix.rx.eureka.server;

import javax.inject.Provider;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.inject.Inject;
import com.netflix.rx.eureka.client.EurekaClient;
import com.netflix.rx.eureka.client.EurekaClients;
import com.netflix.rx.eureka.client.ServerResolver;
import com.netflix.rx.eureka.client.ServerResolver.Protocol;
import com.netflix.rx.eureka.client.ServerResolver.ProtocolType;
import com.netflix.rx.eureka.client.bootstrap.ServerResolvers;
import com.netflix.rx.eureka.client.metric.EurekaClientMetricFactory;
import rx.Observable;
import rx.subjects.ReplaySubject;

import static java.util.Arrays.*;

/**
 * @author Tomasz Bak
 */
public class EurekaClientProvider implements Provider<EurekaClient> {

    private final ReadServerConfig config;
    private final EurekaClientMetricFactory metricFactory;

    private final AtomicBoolean connected = new AtomicBoolean();
    private final ReplaySubject<EurekaClient> replaySubject = ReplaySubject.create();

    @Inject
    public EurekaClientProvider(ReadServerConfig config, EurekaClientMetricFactory metricFactory) {
        this.config = config;
        this.metricFactory = metricFactory;
    }

    // TODO: we are blocking here to not propagate Observable<EurekaClient> further into the code. This will be fixed once Ribbon supports observable load balancer.
    @Override
    public EurekaClient get() {
        if (connected.compareAndSet(false, true)) {
            return connect().take(1).toBlocking().single();
        }
        return replaySubject.take(1).toBlocking().single();
    }

    private Observable<EurekaClient> connect() {
        ServerResolver<InetSocketAddress> resolver = createResolver();
        return EurekaClients.forRegistrationAndDiscovery(resolver, resolver, config.getCodec(), metricFactory);
    }

    private ServerResolver<InetSocketAddress> createResolver() {
        ServerResolver<InetSocketAddress> resolver;

        Protocol[] protocols = {
                new Protocol(config.getWriteClusterRegistrationPort(), ProtocolType.TcpRegistration),
                new Protocol(config.getWriteClusterDiscoveryPort(), ProtocolType.TcpDiscovery)
        };

        if (config.getResolverType() == null) {
            throw new IllegalArgumentException("resolver type not configured");
        }
        switch (config.getResolverType()) {
            case "dns":
                resolver = ServerResolvers.forDomainName(config.getWriteClusterServers()[0], protocols);
                break;
            case "inline":
                Set<Protocol> protocolSet = new HashSet<>(asList(protocols));
                resolver = ServerResolvers.fromList(protocolSet, config.getWriteClusterServers());
                break;
            default:
                throw new IllegalArgumentException("invalid resolver type: " + config.getResolverType());
        }
        return resolver;
    }
}
