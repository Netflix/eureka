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

package com.netflix.rx.eureka.client.bootstrap;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

import com.netflix.rx.eureka.Names;
import com.netflix.rx.eureka.client.EurekaClient;
import com.netflix.rx.eureka.client.EurekaClients;
import com.netflix.rx.eureka.client.ServerResolver;
import com.netflix.rx.eureka.client.ServerResolver.ServerEntry.Action;
import com.netflix.rx.eureka.client.metric.EurekaClientMetricFactory;
import com.netflix.rx.eureka.interests.ChangeNotification;
import com.netflix.rx.eureka.interests.Interest;
import com.netflix.rx.eureka.interests.Interests;
import com.netflix.rx.eureka.registry.InstanceInfo;
import com.netflix.rx.eureka.registry.ServiceSelector;
import rx.Observable;
import rx.functions.Action0;
import rx.functions.Func1;

/**
 * A resolver fetching server list from the Eureka cluster. Eureka client uses
 * this resolver to load read cluster server list from the write cluster, after the
 * registration process.
 *
 * @author Tomasz Bak
 */
public abstract class EurekaServerResolver implements ServerResolver<InetSocketAddress> {

    private final ServerResolver<InetSocketAddress> serverResolver;
    private final Protocol protocol;
    private final EurekaClientMetricFactory metricFactory;
    private final ServiceSelector addressQuery;
    private final Map<String, InetSocketAddress> activeAddresses = new HashMap<>();

    protected EurekaServerResolver(ServerResolver<InetSocketAddress> readClusterResolver, Protocol protocol, EurekaClientMetricFactory metricFactory) {
        this.serverResolver = readClusterResolver;
        this.protocol = protocol;
        this.metricFactory = metricFactory;
        // TODO: address selection process must be more flexible. Right now we try first public IP, followed by any matching discovery service.
        this.addressQuery = ServiceSelector.selectBy()
                .serviceLabel(Names.DISCOVERY).publicIp(true).or()
                .serviceLabel(Names.DISCOVERY);
    }

    protected abstract Interest<InstanceInfo> resolverInterests();

    @Override
    public Observable<ServerEntry<InetSocketAddress>> resolve() {
        final EurekaClient eurekaClient = EurekaClients.forDiscovery(serverResolver, metricFactory);
        return eurekaClient.forInterest(resolverInterests()).flatMap(new Func1<ChangeNotification<InstanceInfo>, Observable<ServerEntry<InetSocketAddress>>>() {
            @Override
            public Observable<ServerEntry<InetSocketAddress>> call(ChangeNotification<InstanceInfo> changeNotification) {
                switch (changeNotification.getKind()) {
                    case Add:
                        return Observable.just(addServerEntry(changeNotification));
                    case Delete:
                        return Observable.just(removeServerEntry(changeNotification));
                    case Modify:
                        return Observable.from(removeServerEntry(changeNotification), addServerEntry(changeNotification));
                }
                return null;
            }
        }).doOnCompleted(new Action0() {
            @Override
            public void call() {
                eurekaClient.close();
            }
        });
    }

    protected ServerEntry<InetSocketAddress> removeServerEntry(ChangeNotification<InstanceInfo> changeNotification) {
        InetSocketAddress activeAddress = activeAddresses.get(changeNotification.getData().getId());
        if (activeAddress == null) {
            return null;
        }
        return new ServerEntry<InetSocketAddress>(Action.Remove, activeAddress, protocol);
    }

    protected ServerEntry<InetSocketAddress> addServerEntry(ChangeNotification<InstanceInfo> changeNotification) {
        InstanceInfo instanceInfo = changeNotification.getData();
        InetSocketAddress address = addressQuery.returnServiceAddress(instanceInfo);
        activeAddresses.put(instanceInfo.getId(), address);
        return new ServerEntry<InetSocketAddress>(Action.Add, address, protocol);
    }

    @Override
    public void start() {
    }

    @Override
    public void close() {
    }

    public static EurekaServerResolver fromVip(ServerResolver<InetSocketAddress> serverResolver, final String vip, Protocol protocol, EurekaClientMetricFactory metricFactory) {
        return new EurekaServerResolver(serverResolver, protocol, metricFactory) {
            @Override
            protected Interest<InstanceInfo> resolverInterests() {
                return Interests.forVips(vip);
            }
        };
    }
}
