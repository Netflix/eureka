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

package com.netflix.eureka2.server.service.replication;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.netflix.eureka2.Server;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.metric.server.WriteServerMetricFactory;
import com.netflix.eureka2.registry.EurekaRegistry;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.server.ReplicationPeerAddressesProvider;
import com.netflix.eureka2.server.config.WriteServerConfig;
import com.netflix.eureka2.server.service.selfinfo.SelfInfoResolver;
import com.netflix.eureka2.utils.rx.RetryStrategyFunc;
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

    private static final Logger logger = LoggerFactory.getLogger(ReplicationService.class);

    private final AtomicReference<STATE> state = new AtomicReference<>(STATE.Idle);
    private final WriteServerConfig config;
    private final EurekaRegistry<InstanceInfo> eurekaRegistry;
    private final SelfInfoResolver selfInfoResolver;
    private final ReplicationPeerAddressesProvider peerAddressesProvider;
    private final WriteServerMetricFactory metricFactory;

    protected final Map<Server, ReplicationSender> addressVsHandler;

    private InstanceInfo ownInstanceInfo;
    private Subscription resolverSubscription;

    @Inject
    public ReplicationService(WriteServerConfig config,
                              EurekaRegistry eurekaRegistry,
                              SelfInfoResolver selfInfoResolver,
                              ReplicationPeerAddressesProvider peerAddressesProvider,
                              WriteServerMetricFactory metricFactory) {
        this.config = config;
        this.eurekaRegistry = eurekaRegistry;
        this.selfInfoResolver = selfInfoResolver;
        this.peerAddressesProvider = peerAddressesProvider;
        this.metricFactory = metricFactory;
        this.addressVsHandler = new HashMap<>();
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

        resolverSubscription = selfInfoResolver.resolve().take(1)
                .switchMap(new Func1<InstanceInfo, Observable<ChangeNotification<Server>>>() {
                    @Override
                    public Observable<ChangeNotification<Server>> call(InstanceInfo instanceInfo) {
                        ownInstanceInfo = instanceInfo;
                        return peerAddressesProvider.get();
                    }
                })
                .retryWhen(new RetryStrategyFunc(1, TimeUnit.SECONDS))
                .subscribe(new Subscriber<ChangeNotification<Server>>() {
                    @Override
                    public void onCompleted() {
                        logger.debug("Replication server resolver stream completed - write cluster server list will no longer be updated");
                    }

                    @Override
                    public void onError(Throwable e) {
                        logger.error("Replication server resolver stream error - write cluster server list will no longer be updated", e);
                    }

                    @Override
                    public void onNext(ChangeNotification<Server> serverNotif) {
                        Server address = serverNotif.getData();
                        switch (serverNotif.getKind()) {
                            case Add:
                                addServer(address);
                                break;
                            case Delete:
                                removeServer(address);
                        }
                    }
                });
    }

    private void addServer(final Server address) {
        if (state.get() == STATE.Closed) {
            logger.info("Not adding server as the service is already shutdown");
            return;
        }

        if (!addressVsHandler.containsKey(address)) {
            logger.info("Adding replication channel to server {}", address);

            ReplicationSender handler = new ReplicationSenderImpl(config, address, eurekaRegistry, ownInstanceInfo, metricFactory);
            addressVsHandler.put(address, handler);
            handler.startReplication();
        }
    }

    private void removeServer(Server address) {
        ReplicationSender handler = addressVsHandler.remove(address);
        if (handler != null) {
            logger.info("Removing replication channel to server {}", address);
            handler.shutdown();
        }
    }

    @PreDestroy
    public void close() {
        logger.info("Closing replication service");
        STATE prev = state.getAndSet(STATE.Closed);
        if (STATE.Connected == prev) {  // only need to perform shutdown if was previously connected
            resolverSubscription.unsubscribe();
            for (ReplicationSender handler : addressVsHandler.values()) {
                handler.shutdown();
            }
            addressVsHandler.clear();
        }
    }
}
