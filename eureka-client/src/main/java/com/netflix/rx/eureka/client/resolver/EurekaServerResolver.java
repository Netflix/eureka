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

package com.netflix.rx.eureka.client.resolver;

import com.netflix.rx.eureka.Names;
import com.netflix.rx.eureka.client.Eureka;
import com.netflix.rx.eureka.client.EurekaClient;
import com.netflix.rx.eureka.interests.ChangeNotification;
import com.netflix.rx.eureka.interests.Interests;
import com.netflix.rx.eureka.registry.InstanceInfo;
import com.netflix.rx.eureka.registry.ServiceSelector;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Func1;
import rx.subjects.BehaviorSubject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A resolver fetching server list from the Eureka cluster. Eureka client uses
 * this resolver to load read cluster server list from the write cluster, after the
 * registration process.
 *
 * @author Tomasz Bak
 */
public class EurekaServerResolver implements ServerResolver {

    private final ServiceSelector addressQuery;
    private final BehaviorSubject<ServerList> serverListSubject;
    private final AtomicReference<ServerList> currentServerList = new AtomicReference<>(new ServerList());
    private final EurekaClient eurekaClient;

    protected EurekaServerResolver(ServerResolver bootstrapResolver, String readServerVip) {
        this(bootstrapResolver, readServerVip, ServiceSelector.selectBy().serviceLabel(Names.DISCOVERY).publicIp(true)
                                                              .or()
                                                              .serviceLabel(Names.DISCOVERY));
    }

    protected EurekaServerResolver(ServerResolver bootstrapResolver, String readServerVip, ServiceSelector serviceSelector) {
        this.addressQuery = serviceSelector;
        serverListSubject = BehaviorSubject.create();
        eurekaClient = Eureka.newClient(bootstrapResolver);
        eurekaClient.forInterest(Interests.forVips(readServerVip))
                    .subscribe(new Subscriber<ChangeNotification<InstanceInfo>>() {
                        @Override
                        public void onCompleted() {
                            eurekaClient.close();
                        }

                        @Override
                        public void onError(Throwable e) {
                            // TODO: Re-subscribe
                        }

                        @Override
                        public void onNext(ChangeNotification<InstanceInfo> changeNotification) {
                            InstanceInfo instanceInfo = changeNotification.getData();
                            InstanceInfo.ServiceEndpoint endpoint = addressQuery.returnServiceEndpoint(instanceInfo);
                            final Server server = new Server(endpoint.getAddress().getIpAddress(),
                                                             endpoint.getServicePort().getPort());
                            final ServerList serverList = currentServerList.get();

                            // This should always be a single threaded invocation, so we do not need CAS
                            switch (changeNotification.getKind()) {
                                case Add:
                                    overwriteServerList(serverList.add(instanceInfo.getId(), server));
                                case Delete:
                                    overwriteServerList(serverList.remove(instanceInfo.getId()));
                                case Modify:
                                    overwriteServerList(serverList.update(instanceInfo.getId(), server));
                            }
                        }
                    });
    }

    private void overwriteServerList(final ServerList serverList) {
        currentServerList.set(serverList);
        serverListSubject.onNext(serverList);
    }

    @Override
    public Observable<Server> resolve() {
        return serverListSubject
                .filter(new Func1<ServerList, Boolean>() {
                    @Override
                    public Boolean call(ServerList serverList) {
                        return !serverList.servers.isEmpty();
                    }
                })
                .take(1)
                .flatMap(new Func1<ServerList, Observable<Server>>() {
                    @Override
                    public Observable<Server> call(ServerList serverList) {
                        return Observable.from(serverList.servers);
                    }
                });
    }

    protected class ServerList {

        private final Map<String, Server> idVsServers;
        private final ArrayList<Server> servers;
        private final int size;
        private final AtomicInteger currentIndex = new AtomicInteger();

        public ServerList(Map<String, Server> servers) {
            this.idVsServers = servers;
            this.servers = new ArrayList<>(this.idVsServers.values());
            this.size = this.servers.size();
        }

        public ServerList() {
            this(new HashMap<String, Server>());
        }

        public Server getNextServer() {
            final int nextIndex = Math.abs(currentIndex.incrementAndGet()) % size;
            return servers.get(nextIndex);
        }

        public ServerList add(String id, Server server) {
            final HashMap<String, Server> idVsServers = new HashMap<>(this.idVsServers); // Copy existing
            return idVsServers.put(id, server) == null ? this : new ServerList(idVsServers);
        }

        public ServerList remove(String id) {
            if (!idVsServers.containsKey(id)) {
                return this;
            }
            final HashMap<String, Server> idVsServers = new HashMap<>(this.idVsServers); // Copy existing
            idVsServers.remove(id);
            return new ServerList(idVsServers);
        }

        public ServerList update(String id, Server server) {
            if (!idVsServers.containsKey(id)) {
                return this;
            }

            final HashMap<String, Server> idVsServers = new HashMap<>(this.idVsServers); // Copy existing
            idVsServers.put(id, server);
            return new ServerList(idVsServers);
        }
    }
}
