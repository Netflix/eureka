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

package com.netflix.eureka2.client;

import com.netflix.eureka2.client.metric.EurekaClientMetricFactory;
import com.netflix.eureka2.client.registry.EurekaClientRegistry;
import com.netflix.eureka2.client.service.EurekaClientRegistryProxy;
import com.netflix.eureka2.client.transport.TransportClient;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.registry.InstanceInfo;
import rx.Observable;

import javax.inject.Singleton;

/**
 * @author Tomasz Bak
 */
@Singleton
public class EurekaClientImpl extends EurekaClient {

    private final EurekaClientRegistry<InstanceInfo> registry;
    private final RegistrationHandler registrationHandler;

    public EurekaClientImpl(EurekaClientRegistry<InstanceInfo> registry,
                            RegistrationHandler registrationHandler) {
        this.registry = registry;
        this.registrationHandler = registrationHandler;
    }

    public EurekaClientImpl(TransportClient writeClient, TransportClient readClient, EurekaClientMetricFactory metricFactory) {
        this(
                readClient == null ? null : new EurekaClientRegistryProxy(readClient, metricFactory),
                writeClient == null ? null : new RegistrationHandlerImpl(writeClient, metricFactory)
        );
    }

    @Override
    public Observable<Void> register(InstanceInfo instanceInfo) {
        return registrationHandler.register(instanceInfo);
    }

    @Override
    public Observable<Void> update(InstanceInfo instanceInfo) {
        return registrationHandler.update(instanceInfo);
    }

    @Override
    public Observable<Void> unregister(InstanceInfo instanceInfo) {
        return registrationHandler.unregister(instanceInfo);
    }

    @Override
    public Observable<ChangeNotification<InstanceInfo>> forInterest(final Interest<InstanceInfo> interest) {
        return registry.forInterest(interest);
    }

    @Override
    public Observable<ChangeNotification<InstanceInfo>> forApplication(String appName) {
        return forInterest(Interests.forApplications(appName));
    }

    @Override
    public Observable<ChangeNotification<InstanceInfo>> forVips(String... vips) {
        return forInterest(Interests.forVips(vips));
    }

    @Override
    public void close() {
        registry.shutdown();
        if (null != registrationHandler) {
            registrationHandler.shutdown();
        }
    }

    @Override
    public String toString() {
        return registry.toString();
    }
}