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

package com.netflix.rx.eureka.server.service;

import javax.annotation.PostConstruct;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.inject.Inject;
import com.netflix.rx.eureka.Names;
import com.netflix.rx.eureka.client.EurekaClient;
import com.netflix.rx.eureka.registry.AddressSelector;
import com.netflix.rx.eureka.registry.DataCenterInfo;
import com.netflix.rx.eureka.registry.InstanceInfo;
import com.netflix.rx.eureka.registry.InstanceInfo.Builder;
import com.netflix.rx.eureka.registry.ServicePort;
import com.netflix.rx.eureka.registry.datacenter.LocalDataCenterInfo;
import com.netflix.rx.eureka.server.ReadServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Notification;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.subjects.ReplaySubject;

/**
 * @author Tomasz Bak
 */
public class ReadSelfRegistrationService implements SelfRegistrationService {

    private static final Logger logger = LoggerFactory.getLogger(ReadSelfRegistrationService.class);

    private final ReadServerConfig config;
    private final EurekaClient eurekaClient;

    private final AtomicBoolean connected = new AtomicBoolean();
    private final ReplaySubject<InstanceInfo> replaySubject = ReplaySubject.create();

    @Inject
    public ReadSelfRegistrationService(ReadServerConfig config, EurekaClient eurekaClient) {
        this.config = config;
        this.eurekaClient = eurekaClient;
    }

    @PostConstruct
    public void registerWithWriteCluster() {
        resolve().subscribe(new Action1<InstanceInfo>() {
            @Override
            public void call(final InstanceInfo instanceInfo) {
                eurekaClient.register(instanceInfo).subscribe(new Subscriber<Void>() {
                    @Override
                    public void onCompleted() {
                        logger.info("Eureka server {} self registration completed", instanceInfo.getId());
                    }

                    @Override
                    public void onError(Throwable e) {
                        logger.error("Eureka server " + instanceInfo.getId() + " self registration failed", e);
                    }

                    @Override
                    public void onNext(Void o) {
                    }
                });
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

                HashSet<ServicePort> ports = new HashSet<>();
                ports.add(new ServicePort(Names.DISCOVERY, config.getDiscoveryPort(), false));

                String address = AddressSelector.selectBy().publicIp(true).or().any().returnNameOrIp(dataCenterInfo.getAddresses());
                HashSet<String> healthCheckUrls = new HashSet<String>();
                healthCheckUrls.add("http://" + address + ':' + config.getWebAdminPort() + "/healthcheck");

                return new Builder()
                        .withId(instanceId)
                        .withApp(config.getAppName())
                        .withVipAddress(config.getVipAddress())
                        .withPorts(ports)
                        .withHealthCheckUrls(healthCheckUrls)
                        .withDataCenterInfo(dataCenterInfo)
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
