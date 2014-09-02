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

package com.netflix.eureka;

import com.netflix.eureka.interests.ChangeNotification;
import com.netflix.eureka.interests.Interest;
import com.netflix.eureka.interests.Interests;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.service.EurekaService;
import com.netflix.eureka.service.InterestChannel;
import com.netflix.eureka.service.RegistrationChannel;
import rx.Observable;

import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Tomasz Bak
 */
public class EurekaClientImpl extends EurekaClient {

    // TODO: Store instances and address multiple interest subscriptions
    // TODO: Serialize calls to interest/registration channel

    /**
     * These channels are eagerly created and updated atomically whenever they are disconnected.
     */
    private final AtomicReference<InterestChannel> interestChannel;
    private final AtomicReference<RegistrationChannel> registrationChannel;

    public EurekaClientImpl(EurekaService service) {
        interestChannel = new AtomicReference<InterestChannel>(service.newInterestChannel());
        registrationChannel = new AtomicReference<RegistrationChannel>(service.newRegistrationChannel());
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
    public Observable<ChangeNotification<InstanceInfo>> forInterest(Interest<InstanceInfo> interest) {
        return getInterestChannel().register(interest); // TODO: Implement multiple interests support
    }

    @Override
    public Observable<ChangeNotification<InstanceInfo>> forVips(String... vips) {
        return forInterest(Interests.forVips(vips));
    }

    @Override
    public Observable<Void> unregisterAllInterest() {
        return getInterestChannel().unregister();
    }

    @Override
    public void close() {
        interestChannel.get().close();
        registrationChannel.get().close();
    }

    protected InterestChannel getInterestChannel() {
        return interestChannel.get(); // TODO: Implement re-initialize on disconnect.
    }

    protected RegistrationChannel getRegistrationChannel() {
        return registrationChannel.get(); // TODO: Implement re-initialize on disconnect.
    }
}
