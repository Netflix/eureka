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

import com.netflix.eureka2.client.interest.EurekaInterestClient;
import com.netflix.eureka2.client.registration.EurekaRegistrationClient;
import com.netflix.eureka2.client.registration.RegistrationObservable;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

/**
 * @author Tomasz Bak
 */
@Singleton
public class EurekaClientImpl implements EurekaClient {

    private static final Logger logger = LoggerFactory.getLogger(EurekaClientImpl.class);

    private final EurekaInterestClient interestClient;
    private final EurekaRegistrationClient registrationClient;

    @Inject
    public EurekaClientImpl(EurekaInterestClient interestClient, EurekaRegistrationClient registrationClient) {
        this.interestClient = interestClient;
        this.registrationClient = registrationClient;
    }

    @Override
    public Observable<ChangeNotification<InstanceInfo>> forInterest(final Interest<InstanceInfo> interest) {
        return interestClient.forInterest(interest);
    }

    @Override
    public RegistrationObservable register(Observable<InstanceInfo> registrant) {
        return registrationClient.register(registrant);
    }

    @Override
    public void shutdown() {
        logger.info("Shutting down eureka client");
        if (null != interestClient) {
            interestClient.shutdown();
        }

        if (null != registrationClient) {
            registrationClient.shutdown();
        }
    }
}