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

package com.netflix.eureka.server.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.netflix.eureka.interests.ChangeNotification;
import com.netflix.eureka.interests.Interest;
import com.netflix.eureka.interests.MultipleInterests;
import com.netflix.eureka.registry.EurekaRegistry;
import com.netflix.eureka.registry.InstanceInfo;
import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.subjects.PublishSubject;

import static java.lang.String.*;

/**
 * Interest notification multiplexer is channel scoped object, so we can depend here
 * on its single-threaded property.
 *
 * @author Tomasz Bak
 */
public class InterestNotificationMultiplexer {

    private final EurekaRegistry eurekaRegistry;

    private final Map<Interest<InstanceInfo>, InterestSubscriber> subscribedInterests = new HashMap<>();
    private final PublishSubject<ChangeNotification<InstanceInfo>> aggregatedStream = PublishSubject.create();

    public InterestNotificationMultiplexer(EurekaRegistry eurekaRegistry) {
        this.eurekaRegistry = eurekaRegistry;
    }

    /**
     * For composite interest, we flatten it first, and than make parallel subscriptions to the registry.
     */
    public void update(Interest<InstanceInfo> interest) {
        if (interest instanceof MultipleInterests) {
            Set<Interest<InstanceInfo>> interests = ((MultipleInterests<InstanceInfo>) interest).flatten();
            // Remove interests not present in the new subscription
            for (Interest<InstanceInfo> currentInterest : subscribedInterests.keySet()) {
                if (!interests.contains(currentInterest)) {
                    subscribedInterests.remove(currentInterest).close();
                }
            }
            // Add new interests
            for (Interest<InstanceInfo> newInterest : interests) {
                if (!subscribedInterests.containsKey(newInterest)) {
                    subscribedInterests.put(newInterest, new InterestSubscriber(newInterest));
                }
            }
        } else {
            // First remove all interests except the current one if present
            for (Interest<InstanceInfo> currentInterest : subscribedInterests.keySet()) {
                if (!interest.equals(currentInterest)) {
                    subscribedInterests.remove(currentInterest).close();
                }
            }
            // Add new interests
            if (subscribedInterests.isEmpty()) {
                subscribedInterests.put(interest, new InterestSubscriber(interest));
            }
        }
    }

    public void unregister() {
        for (Interest<InstanceInfo> currentInterest : subscribedInterests.keySet()) {
            subscribedInterests.remove(currentInterest).close();
        }
    }

    /**
     * Interest channel creates a single subscription to this observable prior to
     * registering any interest set. We can safely use hot observable, which
     * simplifies implementation of the multiplexer.
     */
    public Observable<ChangeNotification<InstanceInfo>> changeNotifications() {
        return aggregatedStream;
    }

    private class InterestSubscriber extends Subscriber<ChangeNotification<InstanceInfo>> {
        private final Interest<InstanceInfo> interest;
        private final Observable<ChangeNotification<InstanceInfo>> registryObservable;
        private final Subscription subscription;

        private InterestSubscriber(Interest<InstanceInfo> interest) {
            this.interest = interest;
            this.registryObservable = eurekaRegistry.forInterest(interest);
            subscription = registryObservable.subscribe(this);
        }

        public void close() {
            subscription.unsubscribe();
        }

        @Override
        public void onCompleted() {
            aggregatedStream.onError(new IllegalStateException(
                    format("Unexpected end of stream for change notification observable for interest %s",
                            interest)));
        }

        @Override
        public void onError(Throwable e) {
            aggregatedStream.onError(new IllegalStateException(
                    format("Unexpected error in stream of change notifications for interest %s",
                            interest)));
        }

        @Override
        public void onNext(ChangeNotification<InstanceInfo> notification) {
            aggregatedStream.onNext(notification);
        }
    }
}
