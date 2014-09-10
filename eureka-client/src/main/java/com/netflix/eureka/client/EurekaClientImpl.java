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
import rx.Subscriber;
import rx.Subscription;
import rx.observers.SafeSubscriber;
import rx.subjects.PublishSubject;

import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Tomasz Bak
 */
public class EurekaClientImpl extends EurekaClient {

    private static final Logger logger = LoggerFactory.getLogger(EurekaClientImpl.class);

    private static final IllegalStateException CLIENT_SHUTTING_DOWN_EXCEPTION =
            new IllegalStateException("The client is shutting down.");

    private final EurekaClientService eurekaService;

    private final AtomicReference<InterestChannel> interestChannel;
    private final AtomicReference<RegistrationChannel> registrationChannel;

    private final PublishSubject<Interest<InstanceInfo>> interestOperations;
    private final Subscription interestOperationsSubscription;

    public EurekaClientImpl(EurekaClientService service) {
        eurekaService = service;
        interestChannel = new AtomicReference<>();
        registrationChannel = new AtomicReference<>(service.newRegistrationChannel());

        interestOperations = PublishSubject.create();
        interestOperationsSubscription = interestOperations.asObservable().subscribe(new InterestOperationsProcessor());
    }

    @Override
    public Observable<Void> register(InstanceInfo instanceInfo) {
        if (getRegistrationChannel() == null) {
            return Observable.error(CLIENT_SHUTTING_DOWN_EXCEPTION);
        }
        return getRegistrationChannel().register(instanceInfo);
    }

    @Override
    public Observable<Void> update(InstanceInfo instanceInfo) {
        if (getRegistrationChannel() == null) {
            return Observable.error(CLIENT_SHUTTING_DOWN_EXCEPTION);
        }
        return getRegistrationChannel().update(instanceInfo);
    }

    @Override
    public Observable<Void> unregister(InstanceInfo instanceInfo) {
        if (getRegistrationChannel() == null) {
            return Observable.error(CLIENT_SHUTTING_DOWN_EXCEPTION);
        }
        return getRegistrationChannel().unregister();
    }

    @Override
    public Observable<ChangeNotification<InstanceInfo>> forInterest(final Interest<InstanceInfo> interest) {
        return Observable.create(new Observable.OnSubscribe<ChangeNotification<InstanceInfo>>() {
            @Override
            public void call(final Subscriber<? super ChangeNotification<InstanceInfo>> subscriber) {
                interestOperations.onNext(interest);
                eurekaService.forInterest(interest).subscribe(new Subscriber<ChangeNotification<InstanceInfo>>() {
                    @Override
                    public void onCompleted() {
                        subscriber.onCompleted();
                    }

                    @Override
                    public void onError(Throwable e) {
                        subscriber.onError(e);
                    }

                    @Override
                    public void onNext(ChangeNotification<InstanceInfo> notification) {
                        subscriber.onNext(notification);
                    }
                });
            }
        });
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
        interestOperationsSubscription.unsubscribe();
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

    /**
     * Subscriber to process forInterest operations serially
     */
    private class InterestOperationsProcessor extends SafeSubscriber<Interest<InstanceInfo>> {
        public InterestOperationsProcessor() {
            super(new Subscriber<Interest<InstanceInfo>>() {
                @Override
                public void onCompleted() {}

                @Override
                public void onError(Throwable e) {}

                @Override
                public void onNext(final Interest<InstanceInfo> interest) {
                    if (getInterestChannel() != null) {
                        getInterestChannel().upgrade(interest).subscribe(new Subscriber<Void>() {
                            @Override
                            public void onCompleted() {}

                            @Override
                            public void onError(Throwable e) {
                                // TODO: handle channel upgrade errors
                                logger.error("channel upgrade error", e);
                            }

                            @Override
                            public void onNext(Void aVoid) {}
                        });
                    } else {
                        interestChannel.set(eurekaService.newInterestChannel());
                        getInterestChannel().register(interest).subscribe(new Subscriber<ChangeNotification<InstanceInfo>>() {
                            @Override
                            public void onCompleted() {}

                            @Override
                            public void onError(Throwable e) {
                                // TODO: handle channel upgrade errors
                                logger.error("channel register error", e);
                            }

                            @Override
                            public void onNext(ChangeNotification<InstanceInfo> notification) {}
                        });
                    }
                }
            });
        }
    }

}