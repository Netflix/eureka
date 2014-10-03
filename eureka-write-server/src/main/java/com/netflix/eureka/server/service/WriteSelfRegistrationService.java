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

package com.netflix.eureka.server.service;

import javax.annotation.PostConstruct;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.inject.Inject;
import com.netflix.eureka.registry.DataCenterInfo;
import com.netflix.eureka.registry.EurekaRegistry;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.registry.InstanceInfo.Builder;
import com.netflix.eureka.registry.datacenter.LocalDataCenterInfo;
import com.netflix.eureka.server.WriteServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Notification;
import rx.Observable;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.subjects.ReplaySubject;

/**
 * Self registration procedure for the Eureka write server.
 *
 * @author Tomasz Bak
 */
public class WriteSelfRegistrationService implements SelfRegistrationService {

    private static final Logger logger = LoggerFactory.getLogger(WriteSelfRegistrationService.class);

    private final WriteServerConfig config;
    private final EurekaRegistry eurekaRegistry;

    private final AtomicBoolean connected = new AtomicBoolean();
    private final ReplaySubject<InstanceInfo> replaySubject = ReplaySubject.create();

    @Inject
    public WriteSelfRegistrationService(WriteServerConfig config, EurekaRegistry eurekaRegistry) {
        this.config = config;
        this.eurekaRegistry = eurekaRegistry;
    }

    @PostConstruct
    public void addToLocalRegistry() {
        resolve().subscribe(new Action1<InstanceInfo>() {
            @Override
            public void call(InstanceInfo instanceInfo) {
                logger.info("Self registration with instance info {}", instanceInfo);
                eurekaRegistry.register(instanceInfo);
            }
        });
    }

    @Override
    public Observable<InstanceInfo> resolve() {
        if (connected.compareAndSet(false, true)) {
            return connect();
        }
        return replaySubject;
    }

    public Observable<InstanceInfo> connect() {
        return resolveDataCenterInfo().take(1).map(new Func1<DataCenterInfo, InstanceInfo>() {
            @Override
            public InstanceInfo call(DataCenterInfo dataCenterInfo) {
                final String instanceId = config.getAppName() + '#' + System.currentTimeMillis();

                HashSet<Integer> ports = new HashSet<Integer>();
                ports.add(config.getRegistrationPort());
                ports.add(config.getReplicationPort());
                ports.add(config.getDiscoveryPort());

                return new Builder()
                        .withId(instanceId)
                        .withApp(config.getAppName())
                        .withVipAddress(config.getVipAddress())
                        .withPorts(ports)
                        .withInstanceLocation(dataCenterInfo)
                        .build();
            }
        }).doOnEach(new Action1<Notification<? super InstanceInfo>>() {
            @Override
            public void call(Notification<? super InstanceInfo> notification) {
                switch (notification.getKind()) {
                    case OnNext:
                        replaySubject.onNext((InstanceInfo) notification.getValue());
                        replaySubject.onCompleted();
                        logger.info("Own instance info resolved to {}", notification.getValue());
                        break;
                    case OnError:
                        replaySubject.onError(notification.getThrowable());
                        logger.error("Could not resolve own instance info", notification.getThrowable());
                        break;
                }
            }
        });
    }

    private Observable<? extends DataCenterInfo> resolveDataCenterInfo() {
        return LocalDataCenterInfo.forDataCenterType(config.getDataCenterType());
    }
}
