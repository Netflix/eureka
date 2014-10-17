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

package com.netflix.rx.eureka.server.replication;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.netflix.rx.eureka.client.ServerResolver.ProtocolType;
import com.netflix.rx.eureka.client.ServerResolver.ServerEntry;
import com.netflix.rx.eureka.client.bootstrap.ServerResolverFilter;
import com.netflix.rx.eureka.registry.EurekaRegistry;
import com.netflix.rx.eureka.registry.InstanceInfo;
import com.netflix.rx.eureka.server.EurekaBootstrapConfig;
import com.netflix.rx.eureka.server.WriteClusterResolverProvider;
import com.netflix.rx.eureka.server.metric.WriteServerMetricFactory;
import com.netflix.rx.eureka.server.service.SelfRegistrationService;
import com.netflix.rx.eureka.transport.EurekaTransports.Codec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Func1;

/**
 * @author Tomasz Bak
 */
@Singleton
public class ReplicationService {

    enum STATE {Idle, Connected, Closed}

    public static final String PEER_RESOLVER_TAG = "peerResolver";
    public static final String RECONNECT_DELAY_TAG = "reconnectDelay";
    public static final String HEART_BEAT_INTERVAL_TAG = "heartbeatInterval";

    private static final Logger logger = LoggerFactory.getLogger(ReplicationService.class);

    // TODO: make this dynamic properties
    private final long reconnectDelay = 30000;
    private final long heartbeatInterval = 5000;

    private final AtomicReference<STATE> state = new AtomicReference<>(STATE.Idle);
    private final EurekaRegistry eurekaRegistry;
    private final SelfRegistrationService selfRegistrationService;
    private final WriteClusterResolverProvider writeClusterResolverProvider;
    private final WriteServerMetricFactory metricFactory;
    private final Codec codec;

    private final Map<InetSocketAddress, ReplicationChannelMonitor> replicationWatchdogs = new HashMap<>();
    private Subscription resolverSubscription;

    @Inject
    public ReplicationService(EurekaBootstrapConfig config,
                              EurekaRegistry eurekaRegistry,
                              SelfRegistrationService selfRegistrationService,
                              WriteClusterResolverProvider writeClusterResolverProvider,
                              WriteServerMetricFactory metricFactory) {
        this.eurekaRegistry = eurekaRegistry;
        this.selfRegistrationService = selfRegistrationService;
        this.writeClusterResolverProvider = writeClusterResolverProvider;
        this.metricFactory = metricFactory;
        this.codec = config.getCodec();
    }

    @PostConstruct
    public void connect() {
        logger.info("Starting replication service");
        if (!state.compareAndSet(STATE.Idle, STATE.Connected)) {
            if (state.get() == STATE.Connected) {
                logger.info("Replication service already started; ignoring subsequent connect");
                return;
            }
            throw new IllegalStateException("ReplicationService already closed");
        }

        resolverSubscription = selfRegistrationService.resolve()
                .flatMap(new Func1<InstanceInfo, Observable<ServerEntry<InetSocketAddress>>>() {
                    @Override
                    public Observable<ServerEntry<InetSocketAddress>> call(InstanceInfo instanceInfo) {
                        return writeClusterResolverProvider.get().resolve().lift(ServerResolverFilter.filterOut(instanceInfo, true));
                    }
                })
                .subscribe(new Subscriber<ServerEntry<InetSocketAddress>>() {
                    @Override
                    public void onCompleted() {
                        logger.debug("Replication server resolver stream completed - write cluster server list will no longer be updated");
                    }

                    @Override
                    public void onError(Throwable e) {
                        logger.error("Replication server resolver stream error - write cluster server list will no longer be updated", e);
                    }

                    @Override
                    public void onNext(ServerEntry<InetSocketAddress> serverEntry) {
                        InetSocketAddress address = new InetSocketAddress(
                                serverEntry.getServer().getAddress(),
                                serverEntry.getPort(ProtocolType.TcpReplication)
                        );
                        switch (serverEntry.getAction()) {
                            case Add:
                                if (!replicationWatchdogs.containsKey(address)) {
                                    logger.debug("Adding replication channel to server " + address);
                                    String targetName = address.toString();
                                    ReplicationChannelMonitor monitor = new ReplicationChannelMonitor(
                                            targetName,
                                            eurekaRegistry,
                                            new ReplicationTransportClient(address, codec, metricFactory.getReplicationServerConnectionMetrics()),
                                            reconnectDelay, heartbeatInterval
                                    );
                                    replicationWatchdogs.put(address, monitor);
                                }
                                break;
                            case Remove:
                                if (replicationWatchdogs.containsKey(address)) {
                                    logger.debug("Removing replication channel to server " + address);
                                    replicationWatchdogs.get(address).close();
                                }
                                break;
                        }
                    }
                });
    }

    @PreDestroy
    public void close() {
        logger.info("Closing replication service");
        if (!state.compareAndSet(STATE.Connected, STATE.Closed)) {
            return;
        }
        resolverSubscription.unsubscribe();
        for (ReplicationChannelMonitor watchdog : replicationWatchdogs.values()) {
            watchdog.close();
        }
    }
}
