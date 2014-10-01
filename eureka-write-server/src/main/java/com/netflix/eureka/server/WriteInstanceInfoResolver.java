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

package com.netflix.eureka.server;

import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;

import com.netflix.eureka.registry.DataCenterInfo;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.registry.InstanceInfo.Builder;
import com.netflix.eureka.registry.datacenter.LocalDataCenterInfo;
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
public class WriteInstanceInfoResolver implements LocalInstanceInfoResolver {

    private static final Logger logger = LoggerFactory.getLogger(WriteInstanceInfoResolver.class);

    private final DataCenterInfo dataCenterInfo;
    private final WriteStartupConfig config;

    private final AtomicBoolean connected = new AtomicBoolean();
    private final ReplaySubject<InstanceInfo> replaySubject = ReplaySubject.create();

    public WriteInstanceInfoResolver(DataCenterInfo dataCenterInfo, WriteStartupConfig config) {
        this.dataCenterInfo = dataCenterInfo;
        this.config = config;
    }

    public Observable<InstanceInfo> resolve() {
        if (connected.compareAndSet(false, true)) {
            return connect().take(1).doOnEach(new Action1<Notification<? super InstanceInfo>>() {
                @Override
                public void call(Notification<? super InstanceInfo> notification) {
                    switch (notification.getKind()) {
                        case OnCompleted:
                            break;
                        case OnError:
                            replaySubject.onError(notification.getThrowable());
                            break;
                        case OnNext:
                            replaySubject.onNext((InstanceInfo) notification.getValue());
                            replaySubject.onCompleted();
                            break;
                    }
                }
            });
        }
        return replaySubject;
    }

    public Observable<InstanceInfo> connect() {
        return resolveDataCenterInfo().map(new Func1<DataCenterInfo, InstanceInfo>() {
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
                        logger.info("Own instance info resolved to {}", notification.getValue());
                        break;
                    case OnError:
                        logger.error("Could not resolve own instance info", notification.getThrowable());
                        break;
                }
            }
        });
    }

    private Observable<? extends DataCenterInfo> resolveDataCenterInfo() {
        if (dataCenterInfo == null) {
            return LocalDataCenterInfo.forDataCenterType(config.getDataCenterType());
        }
        return Observable.just(dataCenterInfo);
    }

    public static LocalInstanceInfoResolver localInstanceInfo(DataCenterInfo dataCenterInfo, WriteStartupConfig config) {
        return new WriteInstanceInfoResolver(dataCenterInfo, config);
    }

    public static LocalInstanceInfoResolver localInstanceInfo(final WriteStartupConfig config) {
        return new WriteInstanceInfoResolver(null, config);
    }
}
