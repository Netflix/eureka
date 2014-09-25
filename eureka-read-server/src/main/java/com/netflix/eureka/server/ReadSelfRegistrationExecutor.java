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

import com.netflix.eureka.client.EurekaClient;
import com.netflix.eureka.registry.DataCenterInfo;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.registry.InstanceInfo.Builder;
import com.netflix.eureka.registry.datacenter.LocalDataCenterInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Subscriber;

/**
 * Self registration procedure for the Eureka read server.
 *
 * @author Tomasz Bak
 */
public class ReadSelfRegistrationExecutor {

    private static final Logger logger = LoggerFactory.getLogger(ReadSelfRegistrationExecutor.class);

    private final EurekaClient eurekaClient;
    private final DataCenterInfo dataCenterInfo;
    private final ReadStartupConfig config;

    protected ReadSelfRegistrationExecutor(EurekaClient eurekaClient, DataCenterInfo dataCenterInfo, ReadStartupConfig config) {
        this.eurekaClient = eurekaClient;
        this.dataCenterInfo = dataCenterInfo;
        this.config = config;
    }

    protected void execute() {
        final String instanceId = config.getAppName() + '#' + System.currentTimeMillis();
        InstanceInfo ownInstanceInfo = new Builder()
                .withId(instanceId)
                .withApp(config.getAppName())
                .withVipAddress(config.getVipAddress())
                .withInstanceLocation(dataCenterInfo)
                .build();
        eurekaClient.register(ownInstanceInfo).subscribe(new Subscriber<Void>() {
            @Override
            public void onCompleted() {
                logger.info("Eureka server {} self registration completed", instanceId);
            }

            @Override
            public void onError(Throwable e) {
                logger.error("Eureka server " + instanceId + " self registration failed", e);
            }

            @Override
            public void onNext(Void o) {
            }
        });
    }

    public static void doSelfRegistration(final EurekaClient eurekaClient, final ReadStartupConfig config) {
        LocalDataCenterInfo.forDataCenterType(config.getDataCenterType()).subscribe(new Subscriber<DataCenterInfo>() {
            @Override
            public void onCompleted() {
            }

            @Override
            public void onError(Throwable e) {
                logger.error("Eureka server self registration failed", e);
            }

            @Override
            public void onNext(DataCenterInfo dataCenterInfo) {
                new ReadSelfRegistrationExecutor(eurekaClient, dataCenterInfo, config).execute();
            }
        });
    }

    public static void doSelfRegistration(final EurekaClient eurekaClient, DataCenterInfo dataCenterInfo,
                                          final ReadStartupConfig config) {
        new ReadSelfRegistrationExecutor(eurekaClient, dataCenterInfo, config).execute();
    }
}
