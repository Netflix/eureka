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

/**
 * @author Tomasz Bak
 */
public class EurekaClientImpl extends EurekaClient {

    private final EurekaService service;
    private final InterestChannel interestChannel;
    private final RegistrationChannel registrationChannel; // TODO: Do only if it is registrable

    public EurekaClientImpl(EurekaService service) {
        this.service = service;
        interestChannel = service.forInterest(null); //TODO: API should change to create an empty channel.
        registrationChannel = service.newRegistrationChannel();
    }

    @Override
    public Observable<Void> update(InstanceInfo instanceInfo) {
        return registrationChannel.update(instanceInfo);
    }

    @Override
    public Observable<ChangeNotification<InstanceInfo>> forInterest(Interest<InstanceInfo> interest) {
        interestChannel.upgrade(interest);
        return interestChannel.asObservable();
    }

    @Override
    public Observable<ChangeNotification<InstanceInfo>> forVips(String... vips) {
        return forInterest(Interests.forVips(vips));
    }

    @Override
    public Observable<Void> close() {
        return null;
    }
}
