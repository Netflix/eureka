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

package com.netflix.eureka.client;

import com.netflix.eureka.client.service.EurekaClientService;
import com.netflix.eureka.interests.ChangeNotification;
import com.netflix.eureka.interests.Interest;
import com.netflix.eureka.interests.Interests;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.service.InterestChannel;
import com.netflix.eureka.service.RegistrationChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Tomasz Bak
 */
public class EurekaClientImpl extends EurekaClient {

    private static final Logger logger = LoggerFactory.getLogger(EurekaClientImpl.class);

    private final EurekaClientService eurekaService;

    private final AtomicReference<InterestChannel> interestChannel;
    private final AtomicReference<RegistrationChannel> registrationChannel;

    private final InterestProcessor interestProcessor;

    public EurekaClientImpl(EurekaClientService service) {
        eurekaService = service;
        interestChannel = new AtomicReference<>(service.newInterestChannel());
        registrationChannel = new AtomicReference<>(service.newRegistrationChannel());

        interestProcessor = new InterestProcessor(interestChannel.get());
    }

    @Override
    public Observable<Void> register(InstanceInfo instanceInfo) {
        return getRegistrationChannel().register(instanceInfo);
    }

    @Override
    public Observable<Void> update(InstanceInfo instanceInfo) {
        return getRegistrationChannel().update(instanceInfo);
    }

    @Override
    public Observable<Void> unregister(InstanceInfo instanceInfo) {
        return getRegistrationChannel().unregister();
    }

    @Override
    public Observable<ChangeNotification<InstanceInfo>> forInterest(final Interest<InstanceInfo> interest) {
        Observable ob = interestProcessor.forInterest(interest);

        @SuppressWarnings("unchecked")
        Observable<ChangeNotification<InstanceInfo>> toReturn = ob.mergeWith(eurekaService.forInterest(interest));

        return toReturn;
    }

    @Override
    public Observable<ChangeNotification<InstanceInfo>> forApplication(String appName) {
        return forInterest(Interests.forApplication(appName));
    }

    @Override
    public Observable<ChangeNotification<InstanceInfo>> forVips(String... vips) {
        return forInterest(Interests.forVips(vips));
    }

    @Override
    public void close() {
        interestProcessor.shutdown();
        InterestChannel iChannel = interestChannel.getAndSet(null);
        if (iChannel != null) {
            iChannel.close();
        }
        RegistrationChannel rChannel = registrationChannel.getAndSet(null);
        if (rChannel != null) {
            rChannel.close();
        }
        eurekaService.shutdown();
    }

    protected InterestChannel getInterestChannel() {
        return interestChannel.get(); // TODO: Implement re-initialize on disconnect.
    }

    protected RegistrationChannel getRegistrationChannel() {
        return registrationChannel.get(); // TODO: Implement re-initialize on disconnect.
    }

    @Override
    public String toString() {
        return eurekaService.toString();
    }
}