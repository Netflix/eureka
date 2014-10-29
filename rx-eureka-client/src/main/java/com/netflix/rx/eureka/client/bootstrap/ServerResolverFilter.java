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
import java.util.HashSet;
import java.util.Set;

import com.netflix.rx.eureka.client.ServerResolver.Protocol;
import com.netflix.rx.eureka.client.ServerResolver.ServerEntry;
import com.netflix.rx.eureka.registry.InstanceInfo;
import com.netflix.rx.eureka.registry.NetworkAddress;
import com.netflix.rx.eureka.registry.ServicePort;
import com.netflix.rx.eureka.utils.SystemUtil;
import rx.Observable.Operator;
import rx.Subscriber;

/**
 * @author Tomasz Bak
 */
public class ServerResolverFilter implements Operator<ServerEntry<InetSocketAddress>, ServerEntry<InetSocketAddress>> {
    private final Set<String> blockedAddresses;
    private final Set<Integer> blockedPorts;

    public ServerResolverFilter(Set<String> blockedAddresses, Set<Integer> blockedPorts) {
        this.blockedAddresses = blockedAddresses;
        this.blockedPorts = blockedPorts;
    }

    @Override
    public Subscriber<? super ServerEntry<InetSocketAddress>> call(final Subscriber<? super ServerEntry<InetSocketAddress>> subscriber) {
        return new Subscriber<ServerEntry<InetSocketAddress>>() {
            @Override
            public void onCompleted() {
                subscriber.onCompleted();
            }

            @Override
            public void onError(Throwable e) {
                subscriber.onError(e);
            }

            @Override
            public void onNext(ServerEntry<InetSocketAddress> serverEntry) {
                String newAddress = serverEntry.getServer().getHostString();
                if (blockedAddresses.contains(newAddress) && blockedPorts != null) {
                    for (Protocol protocol : serverEntry.getProtocols()) {
                        if (blockedPorts.contains(protocol.getPort())) {
                            return;
                        }
                    }
                }
                subscriber.onNext(serverEntry);
            }
        };
    }

    public static ServerResolverFilter filterOut(InstanceInfo instanceInfo, boolean includeLocal) {
        Set<String> addresses = new HashSet<>();
        for (NetworkAddress address : instanceInfo.getDataCenterInfo().getAddresses()) {
            if (address.getHostName() != null) {
                addresses.add(address.getHostName());
            }
            if (address.getIpAddress() != null) {
                addresses.add(address.getIpAddress());
            }
        }

        if (includeLocal) {
            addresses.add(SystemUtil.IP4_LOOPBACK);
            addresses.add(SystemUtil.IP6_LOOPBACK);
            addresses.add("localhost");
        }

        HashSet<ServicePort> servicePorts = instanceInfo.getPorts();
        Set<Integer> portNumbers = new HashSet<>(servicePorts.size());
        for (ServicePort sp : servicePorts) {
            portNumbers.add(sp.getPort());
        }
        return new ServerResolverFilter(addresses, portNumbers);
    }
}
