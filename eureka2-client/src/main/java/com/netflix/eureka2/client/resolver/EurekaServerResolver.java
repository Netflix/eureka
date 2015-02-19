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
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.Names;
import com.netflix.eureka2.client.EurekaClient;
import com.netflix.eureka2.client.EurekaClientBuilder;
import com.netflix.eureka2.client.functions.ChangeNotificationFunctions;
import com.netflix.eureka2.config.BasicEurekaTransportConfig;
import com.netflix.eureka2.config.EurekaTransportConfig;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.metric.EurekaRegistryMetricFactory;
import com.netflix.eureka2.metric.client.EurekaClientMetricFactory;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.registry.instance.NetworkAddress.ProtocolType;
import com.netflix.eureka2.registry.selector.ServiceSelector;
import com.netflix.eureka2.Server;
import netflix.ocelli.LoadBalancer;
import netflix.ocelli.LoadBalancerBuilder;
import netflix.ocelli.MembershipEvent;
import netflix.ocelli.MembershipEvent.EventType;
import netflix.ocelli.loadbalancer.DefaultLoadBalancerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.functions.Action0;
import rx.functions.Func1;
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
 * As we are using {@link EurekaClient} in this resolver, the retries are handled by it.
 * To prevent from indefinite halt (due to lack of connectivity), there is a timeout
 * configured for how long we want to wait for the fresh data.
 *
 * If fresh data fetch operation fails, and load balancer server list is empty, the last
 * error is returned to the client. If the subscription stream returned empty stream, the stale
 * data are used in the reply.
 *
 * @author Tomasz Bak
 */
public class EurekaServerResolver implements ServerResolver {
    private static final Logger logger = LoggerFactory.getLogger(EurekaServerResolver.class);

    /**
     * We need to timeout resolve operation so we can fallback to the stale registry content.
     * More sophisticated implementation would be to wait indefinitely if there available
     * server list is empty.
     */
    private static final long RESOLVE_TIMEOUT_MS = 30000;

    private final Interest<InstanceInfo> readServerInterest;
    private final ServiceSelector serviceSelector;

    private final LoadBalancer<Server> loadBalancer;
    private final PublishSubject<MembershipEvent<Server>> loadBalancerUpdates = PublishSubject.create();
    private final EurekaClientBuilder eurekaClientBuilder;
    private Set<Server> lastServerSet = new HashSet<>();

    public EurekaServerResolver(
            EurekaClientBuilder eurekaClientBuilder,
            Interest<InstanceInfo> readServerInterest,
            ServiceSelector serviceSelector,
            LoadBalancerBuilder<Server> loadBalancerBuilder) {
        this.eurekaClientBuilder = eurekaClientBuilder;
        this.readServerInterest = readServerInterest;
        this.serviceSelector = serviceSelector;
        this.loadBalancer = loadBalancerBuilder.withMembershipSource(loadBalancerUpdates).build();
    }

    @Override
    public Observable<Server> resolve() {
        return fetchFreshServerSet()
                .timeout(RESOLVE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .flatMap(new Func1<Set<Server>, Observable<Server>>() {
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
                            logger.error("resolver returned an error; keeping the previous data", error);
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
        final EurekaClient eurekaClient = eurekaClientBuilder.build();
        try {
            return eurekaClient.forInterest(readServerInterest)
                    .compose(ChangeNotificationFunctions.<InstanceInfo>buffers())
                    .compose(ChangeNotificationFunctions.<InstanceInfo>snapshots())
                    .take(1)
                    .map(new Func1<Set<InstanceInfo>, Set<Server>>() {
                        @Override
                        public Set<Server> call(Set<InstanceInfo> notifications) {
                            Set<Server> servers = new HashSet<Server>();
                            for (InstanceInfo item : notifications) {
                                InetSocketAddress socketAddress = serviceSelector.returnServiceAddress(item);
                                Server newServer = new Server(socketAddress.getHostString(), socketAddress.getPort());
                                servers.add(newServer);
                            }
                            return servers;
                        }
                    })
                    .doOnTerminate(new Action0() {
                        @Override
                        public void call() {
                            eurekaClient.shutdown();
                        }
                    });
        } catch (Exception e) {
            eurekaClient.shutdown();
            throw e;
        }
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
    }

    public static class EurekaServerResolverBuilder {

        private EurekaTransportConfig transportConfig;
        private ServerResolver bootstrapResolver;
        private Interest<InstanceInfo> readServerInterest;
        private LoadBalancerBuilder<Server> loadBalancerBuilder;
        private ServiceSelector serviceSelector;
        private EurekaClientMetricFactory clientMetricFactory;
        private EurekaRegistryMetricFactory registryMetricFactory;

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

        public EurekaServerResolverBuilder withClientMetricFactory(EurekaClientMetricFactory metricFactory) {
            this.clientMetricFactory = metricFactory;
            return this;
        }

        public EurekaServerResolverBuilder withRegistryMetricFactory(EurekaRegistryMetricFactory metricFactory) {
            this.registryMetricFactory = metricFactory;
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
                transportConfig = new BasicEurekaTransportConfig.Builder().build();
            }
            if (clientMetricFactory == null) {
                clientMetricFactory = EurekaClientMetricFactory.clientMetrics();
            }
            if (registryMetricFactory == null) {
                registryMetricFactory = EurekaRegistryMetricFactory.registryMetrics();
            }

            EurekaClientBuilder eurekaClientBuilder = EurekaClientBuilder.newBuilder()
                    .withClientMetricFactory(clientMetricFactory)
                    .withRegistryMetricFactory(registryMetricFactory)
                    .withWriteServerResolver(ServerResolvers.just("localhost", 0)) // TODO We have provide some resolver, otherwise validation fails
                    .withReadServerResolver(bootstrapResolver)
                    .withTransportConfig(transportConfig);
            return new EurekaServerResolver(eurekaClientBuilder, readServerInterest, serviceSelector, loadBalancerBuilder);
        }
    }
}
