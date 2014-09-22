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

package com.netflix.eureka.server.audit.kafka;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import com.netflix.eureka.client.EurekaClient;
import com.netflix.eureka.client.EurekaClients;
import com.netflix.eureka.client.ServerResolver;
import com.netflix.eureka.interests.ChangeNotification;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.registry.NetworkAddress;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Subscriber;
import rx.functions.Action1;

/**
 * {@link ServerList} provider for Ribbon {@link ILoadBalancer}. This is utility class
 * that should be shared internally and provided to external users.
 *
 * TODO: move it out to different package
 *
 * @author Tomasz Bak
 */
public class EurekaSourcedServerList implements ServerList<Server> {

    private static final Logger logger = LoggerFactory.getLogger(EurekaSourcedServerList.class);

    private final ServerResolver<InetSocketAddress> readClusterResolver;
    private final String vip;
    private final int defaultPort;
    private volatile EurekaClient eurekaClient;
    private final ConcurrentHashMap<String, Server> servers = new ConcurrentHashMap<>();

    public EurekaSourcedServerList(ServerResolver<InetSocketAddress> readClusterResolver, String vip, int defaultPort) {
        this.readClusterResolver = readClusterResolver;
        this.vip = vip;
        this.defaultPort = defaultPort;
    }

    @PostConstruct
    public void start() {
        EurekaClients.forDiscovery(readClusterResolver).subscribe(new Action1<EurekaClient>() {
            @Override
            public void call(EurekaClient eurekaClient) {
                EurekaSourcedServerList.this.eurekaClient = eurekaClient;
                subscribeToVip();
            }
        });
    }

    protected void subscribeToVip() {
        logger.debug("Starting ServerList subscription to {}", vip);
        eurekaClient.forVips(vip).subscribe(new ServerListUpdater());
    }

    @PreDestroy
    public void stop() {
        if (eurekaClient != null) {
            eurekaClient.close();
            eurekaClient = null;
        }
    }

    @Override
    public List<Server> getInitialListOfServers() {
        return getUpdatedListOfServers();
    }

    @Override
    public List<Server> getUpdatedListOfServers() {
        return new ArrayList<>(servers.values());
    }

    private class ServerListUpdater extends Subscriber<ChangeNotification<InstanceInfo>> {
        @Override
        public void onCompleted() {
            // This should only happen when EurekaClient is closed.
            logger.debug("ServerListUpdater completed");
        }

        @Override
        public void onError(Throwable e) {
            if (logger.isDebugEnabled()) {
                logger.debug("Interest channel for " + vip + " completed with error", e);
            }
            // The contract states that we shall subscribe again.
            if (eurekaClient != null) {
                subscribeToVip();
            }
        }

        @Override
        public void onNext(ChangeNotification<InstanceInfo> notification) {
            // TODO: this should be unqiue instanceId, that stays the same even after server restart
            InstanceInfo instanceInfo = notification.getData();
            String instanceId = instanceInfo.getId();
            switch (notification.getKind()) {
                case Add:
                case Modify:
                    NetworkAddress address = instanceInfo.getDataCenterInfo().getFirstPublicAddress();
                    servers.put(instanceId, new Server(address.getIpAddress(), defaultPort));
                case Delete:
                    servers.remove(instanceId);
            }
        }
    }
}
