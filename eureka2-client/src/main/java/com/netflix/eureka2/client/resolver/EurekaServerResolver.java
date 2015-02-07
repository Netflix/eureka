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

package com.netflix.eureka2.client.resolver;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.netflix.eureka2.Names;
import com.netflix.eureka2.client.channel.SnapshotInterestChannel;
import com.netflix.eureka2.client.channel.SnapshotInterestChannelImpl;
import com.netflix.eureka2.client.transport.TransportClients;
import com.netflix.eureka2.client.transport.tcp.TcpDiscoveryClient;
import com.netflix.eureka2.config.BasicEurekaTransportConfig;
import com.netflix.eureka2.config.EurekaTransportConfig;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.registry.instance.NetworkAddress.ProtocolType;
import com.netflix.eureka2.registry.selector.ServiceSelector;
import com.netflix.eureka2.utils.rx.RetryStrategyFunc;
import netflix.ocelli.LoadBalancer;
import netflix.ocelli.LoadBalancerBuilder;
import netflix.ocelli.MembershipEvent;
import netflix.ocelli.MembershipEvent.EventType;
import netflix.ocelli.loadbalancer.DefaultLoadBalancerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.functions.Func1;
import rx.functions.Func2;
import rx.subjects.PublishSubject;

/**
 * A resolver for selecting read server node from a notification stream
 * returned by a write server node.
 *
 * To minimize load on the write cluster, a short running connection is established
 * to fetch latest read cluster server list, on each resolve invocation to refresh
 * server list in the load balancer. If the data cannot be refreshed, the stale
 * load balancer content is used.
 *
 * <h1>Failure scenarios</h1>
 *
 * As a general rule, the resolver returns any available data rather than failing
 * due to an error. If the internal load balancer has at least one item, this item
 * will be returned (even if the data is stale).
 *
 * If there is an issue with connecting to an interest channel, the request is retried.
 * If all retries fail, and load balancer server list is empty, the last error is
 * returned to the client. If the subscription stream returned empty stream, the stale
 * data are used in the reply.
 *
 * @author Tomasz Bak
 */
public class EurekaServerResolver implements ServerResolver {
    private static final Logger logger = LoggerFactory.getLogger(EurekaServerResolver.class);

    private final SnapshotInterestChannel snapshotInterestChannel;
    private final Interest<InstanceInfo> readServerInterest;
    private final ServiceSelector serviceSelector;

    private final LoadBalancer<Server> loadBalancer;
    private final PublishSubject<MembershipEvent<Server>> loadBalancerUpdates = PublishSubject.create();
    private Set<Server> lastServerSet = new HashSet<>();

    public EurekaServerResolver(
            SnapshotInterestChannel snapshotInterestChannel,
            Interest<InstanceInfo> readServerInterest,
            ServiceSelector serviceSelector,
            LoadBalancerBuilder<Server> loadBalancerBuilder) {
        this.snapshotInterestChannel = snapshotInterestChannel;
        this.readServerInterest = readServerInterest;
        this.serviceSelector = serviceSelector;
        this.loadBalancer = loadBalancerBuilder.withMembershipSource(loadBalancerUpdates).build();
    }

    @Override
    public Observable<Server> resolve() {
        return fetchFreshServerSet().flatMap(new Func1<Set<Server>, Observable<Server>>() {
            @Override
            public Observable<Server> call(Set<Server> newServers) {
                if (!newServers.isEmpty()) {
                    updateLoadBalancer(newServers);
                } else {
                    logger.info("resolver returned empty server list; keeping the previous data");
                }
                return loadBalancer.choose();
            }
        }).onErrorResumeNext(new Func1<Throwable, Observable<Server>>() {
            @Override
            public Observable<Server> call(Throwable error) {
                // We use stale data if available in case of an error
                if (!lastServerSet.isEmpty()) {
                    return loadBalancer.choose();
                }
                return Observable.error(error);
            }
        });
    }

    /**
     * Fetch a new set of servers from the write cluster. This function returns always
     * exactly one item with list of retrieved servers, or an empty list, or an error.
     */
    private Observable<Set<Server>> fetchFreshServerSet() {
        return snapshotInterestChannel
                .forSnapshot(readServerInterest)
                .reduce(new HashSet<Server>(), new Func2<Set<Server>, InstanceInfo, Set<Server>>() {
                    @Override
                    public Set<Server> call(Set<Server> servers, InstanceInfo newInstanceInfo) {
                        InetSocketAddress socketAddress = serviceSelector.returnServiceAddress(newInstanceInfo);
                        Server newServer = new Server(socketAddress.getHostString(), socketAddress.getPort());
                        servers.add(newServer);
                        return servers;
                    }
                })
                .retryWhen(new RetryStrategyFunc(500, 2, true))
                .concatWith(Observable.just(Collections.<Server>emptySet()))
                .take(1);
    }

    /**
     * This function removes from load balancer all server entries not present in
     * the new server set, and adds all the new entries.
     */
    private void updateLoadBalancer(Set<Server> newServers) {
        // Remove
        HashSet<Server> toRemove = new HashSet<>(lastServerSet);
        toRemove.removeAll(newServers);
        logger.info("Removing load balancer entries: {}", toRemove);
        for (Server server : toRemove) {
            loadBalancerUpdates.onNext(new MembershipEvent<Server>(EventType.REMOVE, server));
        }

        // Add
        HashSet<Server> toAdd = new HashSet<>(newServers);
        toAdd.removeAll(lastServerSet);
        logger.info("Adding load balancer entries: {}", toAdd);
        for (Server server : toAdd) {
            loadBalancerUpdates.onNext(new MembershipEvent<Server>(EventType.ADD, server));
        }
        lastServerSet = newServers;
    }

    @Override
    public void close() {
        loadBalancer.shutdown();
        snapshotInterestChannel.close();
    }

    public static class EurekaServerResolverBuilder {

        private EurekaTransportConfig transportConfig;
        private ServerResolver bootstrapResolver;
        private Interest<InstanceInfo> readServerInterest;
        private LoadBalancerBuilder<Server> loadBalancerBuilder;
        private ServiceSelector serviceSelector;

        public EurekaServerResolverBuilder withTransportConfig(EurekaTransportConfig transportConfig) {
            this.transportConfig = transportConfig;
            return this;
        }

        public EurekaServerResolverBuilder withBootstrapResolver(ServerResolver bootstrapResolver) {
            this.bootstrapResolver = bootstrapResolver;
            return this;
        }

        public EurekaServerResolverBuilder withReadServerInterest(Interest<InstanceInfo> readServerInterest) {
            this.readServerInterest = readServerInterest;
            return this;
        }

        public EurekaServerResolverBuilder withServiceSelector(ServiceSelector serviceSelector) {
            this.serviceSelector = serviceSelector;
            return this;
        }

        public EurekaServerResolverBuilder withLoadBalancerBuilder(LoadBalancerBuilder<Server> loadBalancerBuilder) {
            this.loadBalancerBuilder = loadBalancerBuilder;
            return this;
        }

        public EurekaServerResolver build() {
            if (serviceSelector == null) {
                serviceSelector = ServiceSelector.selectBy()
                        .serviceLabel(Names.DISCOVERY).protocolType(ProtocolType.IPv4).publicIp(true)
                        .or()
                        .serviceLabel(Names.DISCOVERY).protocolType(ProtocolType.IPv4);
            }
            if (loadBalancerBuilder == null) {
                loadBalancerBuilder = new DefaultLoadBalancerBuilder<>(null);
            }
            if (bootstrapResolver == null) {
                throw new IllegalStateException("BootstrapResolver property not set");
            }
            if (transportConfig == null) {
                transportConfig = new BasicEurekaTransportConfig();
            }

            TcpDiscoveryClient transportClient =
                    (TcpDiscoveryClient) TransportClients.newTcpDiscoveryClient(transportConfig, bootstrapResolver);
            SnapshotInterestChannel snapshotInterestChannel = new SnapshotInterestChannelImpl(transportClient);
            return new EurekaServerResolver(snapshotInterestChannel, readServerInterest, serviceSelector, loadBalancerBuilder);
        }
    }
}
