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

import com.netflix.eureka.interests.ChangeNotification;
import com.netflix.eureka.interests.Interest;
import com.netflix.eureka.interests.Interests;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.registry.LeasedInstanceRegistry;
import com.netflix.eureka.service.EurekaService;
import com.netflix.eureka.service.InterestChannel;
import com.netflix.eureka.service.RegistrationChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscriber;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Tomasz Bak
 */
public class EurekaClientImpl extends EurekaClient {

    private static final Logger logger = LoggerFactory.getLogger(EurekaClientImpl.class);

    // TODO: Store instances and address multiple interest subscriptions
    // TODO: Serialize calls to interest/registration channel

    private final EurekaService eurekaService;

    /**
     * These channels are eagerly created and updated atomically whenever they are disconnected.
     */
    private final AtomicReference<InterestChannel> interestChannel;  // TODO: there is no need to make this atomicRef
    private final AtomicReference<RegistrationChannel> registrationChannel;

    private final Lock interestLock = new ReentrantLock();

    private final LeasedInstanceRegistry clientRegistry;

    public EurekaClientImpl(EurekaService service) {
        eurekaService = service;
        interestChannel = new AtomicReference<>();
        registrationChannel = new AtomicReference<>(service.newRegistrationChannel());

        // TODO: have a client specific implementation/interface the access to indexRegistry?
        // TODO: provide local instanceInfo for the registry
        // TODO: interface interestRegistry management calls
        clientRegistry = new LeasedInstanceRegistry(null);
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
    public Observable<ChangeNotification<InstanceInfo>> forInterest(final Interest<InstanceInfo> interest) {
        try {
            interestLock.lock();  // TODO: evaluate lock strategy to use

            if (getInterestChannel() != null) {
                getInterestChannel().upgrade(interest).subscribe();
            } else {
                interestChannel.set(eurekaService.newInterestChannel());
                getInterestChannel().register(interest).subscribe(new Subscriber<ChangeNotification<InstanceInfo>>() {
                    @Override
                    public void onCompleted() {
                        clientRegistry.getIndexRegistry().completeInterest(interest);
                        logger.debug("Interest " + interest + " has completed");
                    }

                    @Override
                    public void onError(Throwable e) {
                        clientRegistry.getIndexRegistry().errorInterest(interest, e);
                        logger.error("Error fetching interest " + interest, e);
                    }

                    @Override
                    public void onNext(ChangeNotification<InstanceInfo> notification) {
                        switch (notification.getKind()) {
                            case Add:
                                clientRegistry.register(notification.getData());
                                break;
                            case Modify:
                                clientRegistry.update(notification.getData(), null);
                                break;
                            case Delete:
                                clientRegistry.unregister(notification.getData().getId());
                                break;
                            default:
                                logger.error("Unrecognized notification kind");
                        }
                    }
                });
            }

            return clientRegistry.forInterest(interest);
        } finally {
            interestLock.unlock();
        }
    }

    @Override
    public Observable<ChangeNotification<InstanceInfo>> forVips(String... vips) {
        return forInterest(Interests.forVips(vips));
    }

    @Override
    public Observable<Void> unregisterAllInterest() {
        try {
            interestLock.lock();  // TODO: evaluate lock strategy to use

            clientRegistry.getIndexRegistry().shutdown();  // TODO: what to do with the stale registry data?
            InterestChannel old = interestChannel.getAndSet(null);
            old.unregister().subscribe();
            old.close();

            return Observable.empty();
        } finally {
            interestLock.unlock();
        }
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

    @Override
    public String toString() {
        return clientRegistry.toString();
    }
}
