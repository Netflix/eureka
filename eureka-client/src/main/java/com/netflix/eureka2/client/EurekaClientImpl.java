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

import javax.inject.Inject;
import javax.inject.Singleton;

import com.netflix.eureka2.client.registration.RegistrationHandler;
import com.netflix.eureka2.client.registry.EurekaClientRegistry;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import rx.Observable;

/**
 * @author Tomasz Bak
 */
@Singleton
public class EurekaClientImpl extends EurekaClient {

    private final EurekaClientRegistry<InstanceInfo> clientRegistry;
    private final RegistrationHandler registrationHandler;

    @Inject
    public EurekaClientImpl(EurekaClientRegistry clientRegistry, RegistrationHandler registrationHandler) {
        this.clientRegistry = clientRegistry;
        this.registrationHandler = registrationHandler;
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
        return clientRegistry.forInterest(interest);
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
        clientRegistry.shutdown();
        if (null != registrationHandler) {
            registrationHandler.shutdown();
        }
    }

    @Override
    public String toString() {
        return clientRegistry.toString();
    }
}