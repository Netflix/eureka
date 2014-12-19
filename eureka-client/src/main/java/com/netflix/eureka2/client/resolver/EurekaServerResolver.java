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
import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.Names;
import com.netflix.eureka2.client.Eureka;
import com.netflix.eureka2.client.EurekaClient;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.registry.InstanceInfo;
import com.netflix.eureka2.registry.InstanceInfo.Status;
import com.netflix.eureka2.registry.NetworkAddress.ProtocolType;
import com.netflix.eureka2.registry.ServiceSelector;
import netflix.ocelli.LoadBalancer;
import netflix.ocelli.LoadBalancerBuilder;
import netflix.ocelli.MembershipEvent;
import netflix.ocelli.loadbalancer.DefaultLoadBalancerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.functions.Action0;
import rx.functions.Func1;

import static netflix.ocelli.MembershipEvent.EventType.ADD;
import static netflix.ocelli.MembershipEvent.EventType.REMOVE;

/**
 * A resolver fetching server list from the Eureka cluster. Eureka client uses
 * this resolver to load read cluster server list from the write cluster, after the
 * registration process.
 *
 * @author Tomasz Bak
 */
public class EurekaServerResolver implements ServerResolver {

    private static final Logger logger = LoggerFactory.getLogger(EurekaServerResolver.class);

    private final String readServerVip;
    private final ServiceSelector serviceSelector;
    private final LoadBalancerBuilder<Server> loadBalancerBuilder;
    private LoadBalancer<Server> loadBalancer;
    private final EurekaClient eurekaClient;

    public EurekaServerResolver(
            ServerResolver bootstrapResolver,
            String readServerVip,
            ServiceSelector serviceSelector,
            LoadBalancerBuilder<Server> loadBalancerBuilder) {
        this(Eureka.newClientBuilder(bootstrapResolver).build(), readServerVip, serviceSelector, loadBalancerBuilder);
    }

    public EurekaServerResolver(
            EurekaClient eurekaClient,
            String readServerVip,
            ServiceSelector serviceSelector,
            LoadBalancerBuilder<Server> loadBalancerBuilder) {
        this.eurekaClient = eurekaClient;
        this.readServerVip = readServerVip;
        this.serviceSelector = serviceSelector;
        this.loadBalancerBuilder = loadBalancerBuilder;
    }

    @Override
    public Observable<Server> resolve() {
        if (loadBalancer == null) {
            loadBalancer = loadBalancerBuilder.withMembershipSource(serverUpdates()).build();
        }

        return loadBalancer.choose();
    }

    @Override
    public void close() {
        eurekaClient.close();
        if (loadBalancer != null) {
            loadBalancer.shutdown();
            loadBalancer = null;
        }
    }

    // FIXME: simulate stream completion with a timeout. Remove once we have the bootstrap API
    protected Observable<MembershipEvent<Server>> serverUpdates() {
        return eurekaClient.forVips(readServerVip)
                .timeout(5000, TimeUnit.MILLISECONDS, Observable.<ChangeNotification<InstanceInfo>>empty())
                .map(new Func1<ChangeNotification<InstanceInfo>, MembershipEvent<Server>>() {
                    @Override
                    public MembershipEvent<Server> call(ChangeNotification<InstanceInfo> notification) {
                        InstanceInfo info = notification.getData();
                        InetSocketAddress address = serviceSelector.returnServiceAddress(info);
                        if (address == null) {
                            logger.warn("Unable to determine Eureka read server address/port from provided instance info: {}", info);
                            return null;
                        }
                        Server server = new Server(address.getHostString(), address.getPort());
                        switch (notification.getKind()) {
                            case Add:
                                if (info.getStatus() == Status.UP) {
                                    return new MembershipEvent<>(ADD, server);
                                }
                                break;
                            case Modify:
                                if (info.getStatus() == Status.UP) {
                                    return new MembershipEvent<>(ADD, server);
                                }
                                return new MembershipEvent<>(REMOVE, server);
                            case Delete:
                                return new MembershipEvent<>(REMOVE, server);
                        }
                        return null;
                    }
                })
                .doOnCompleted(new Action0() {
                    @Override
                    public void call() {
                        logger.info("Stream to resolve read servers completed");
                    }
                });
    }

    public static class EurekaServerResolverBuilder {

        private ServerResolver bootstrapResolver;
        private EurekaClient eurekaClient;
        private String readServerVip;
        private LoadBalancerBuilder<Server> loadBalancerBuilder;
        private ServiceSelector serviceSelector;

        public EurekaServerResolverBuilder withBootstrapResolver(ServerResolver bootstrapResolver) {
            this.bootstrapResolver = bootstrapResolver;
            return this;
        }

        public EurekaServerResolverBuilder withEurekaClient(EurekaClient eurekaClient) {
            this.eurekaClient = eurekaClient;
            return this;
        }

        public EurekaServerResolverBuilder withReadServerVip(String readServerVip) {
            this.readServerVip = readServerVip;
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
                serviceSelector = ServiceSelector.selectBy().serviceLabel(Names.DISCOVERY).protocolType(ProtocolType.IPv4);
            }
            if (loadBalancerBuilder == null) {
                loadBalancerBuilder = new DefaultLoadBalancerBuilder<>(null);
            }
            if (eurekaClient != null && bootstrapResolver != null) {
                throw new IllegalArgumentException("Both EurekaClient and bootstrap ServerResolver provided, while only one of the two expected");
            }
            if (eurekaClient != null) {
                return new EurekaServerResolver(eurekaClient, readServerVip, serviceSelector, loadBalancerBuilder);
            }
            if (bootstrapResolver != null) {
                return new EurekaServerResolver(bootstrapResolver, readServerVip, serviceSelector, loadBalancerBuilder);
            }
            return null;
        }
    }
}
