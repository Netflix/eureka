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
import java.util.concurrent.atomic.AtomicReference;

import com.netflix.rx.eureka.client.EurekaClient;
import com.netflix.rx.eureka.client.EurekaClients;
import com.netflix.rx.eureka.client.ServerResolver;
import com.netflix.rx.eureka.client.ServerResolver.ServerEntry.Action;
import com.netflix.rx.eureka.client.metric.EurekaClientMetricFactory;
import com.netflix.rx.eureka.interests.ChangeNotification;
import com.netflix.rx.eureka.interests.Interest;
import com.netflix.rx.eureka.interests.Interests;
import com.netflix.rx.eureka.registry.InstanceInfo;
import rx.Observable;
import rx.functions.Action0;
import rx.functions.Action1;
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

    protected EurekaServerResolver(ServerResolver<InetSocketAddress> readClusterResolver, Protocol protocol, EurekaClientMetricFactory metricFactory) {
        this.serverResolver = readClusterResolver;
        this.protocol = protocol;
        this.metricFactory = metricFactory;
    }

    protected abstract Interest<InstanceInfo> resolverInterests();

    @Override
    public Observable<ServerEntry<InetSocketAddress>> resolve() {
        final AtomicReference<EurekaClient> eurekaClientRef = new AtomicReference<>();
        return EurekaClients.forDiscovery(serverResolver, metricFactory)
                .take(1)
                .doOnNext(new Action1<EurekaClient>() {
                    @Override
                    public void call(EurekaClient eurekaClient) {
                        eurekaClientRef.set(eurekaClient);
                    }
                })
                .flatMap(new Func1<EurekaClient, Observable<ChangeNotification<InstanceInfo>>>() {
                    @Override
                    public Observable<ChangeNotification<InstanceInfo>> call(EurekaClient eurekaClient) {
                        return eurekaClient.forInterest(resolverInterests());
                    }
                }).map(new Func1<ChangeNotification<InstanceInfo>, ServerEntry<InetSocketAddress>>() {
                    @Override
                    public ServerEntry<InetSocketAddress> call(ChangeNotification<InstanceInfo> changeNotification) {
                        switch (changeNotification.getKind()) {
                            case Add:
                                return new ServerEntry<InetSocketAddress>(
                                        Action.Add,
                                        // TODO: address selection process must be improved
                                        new InetSocketAddress(changeNotification.getData().getDataCenterInfo().getFirstPublicAddress().getIpAddress(), 0),
                                        protocol
                                );
                            case Delete:
                                return new ServerEntry<InetSocketAddress>(
                                        Action.Remove,
                                        new InetSocketAddress(changeNotification.getData().getDataCenterInfo().getFirstPublicAddress().getIpAddress(), 0),
                                        protocol
                                );
                            case Modify:
                                // Ignore
                        }
                        return null;
                    }
                }).doOnCompleted(new Action0() {
                    @Override
                    public void call() {
                        eurekaClientRef.get().close();
                    }
                });
    }

    @Override
    public void start() {
    }

    @Override
    public void close() {
    }

    public static EurekaServerResolver fromVip(ServerResolver<InetSocketAddress> serverResolver, final String vip, Protocol protocol, EurekaClientMetricFactory metricFactory) {
        return new EurekaServerResolver(serverResolver, protocol,metricFactory) {
            @Override
            protected Interest<InstanceInfo> resolverInterests() {
                return Interests.forVip(vip);
            }
        };
    }
}
