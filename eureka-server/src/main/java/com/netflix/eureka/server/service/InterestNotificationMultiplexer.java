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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.netflix.eureka.interests.ChangeNotification;
import com.netflix.eureka.interests.Interest;
import com.netflix.eureka.interests.MultipleInterests;
import com.netflix.eureka.registry.EurekaRegistry;
import com.netflix.eureka.registry.InstanceInfo;
import rx.Observable;
import rx.observables.ConnectableObservable;
import rx.subjects.PublishSubject;

/**
 * Interest notification multiplexer is channel scoped object, so we can depend here
 * on its single-threaded property from the channel side. However as we subscribe
 * to multiple observables we need to merge them properly. For that we use
 * {@link rx.Observable#merge} with bridge {@link rx.subjects.PublishSubject} wrapping
 * each change notification stream. The bridge subjects are stored in a map, so when an
 * interest must be removed during upgrade, we can just complete its corresponding
 * bridge subject.
 *
 * @author Tomasz Bak
 */
public class InterestNotificationMultiplexer {

    private final EurekaRegistry<InstanceInfo> eurekaRegistry;

    private final Map<Interest<InstanceInfo>, PublishSubject<ChangeNotification<InstanceInfo>>> subscriptions = new HashMap<>();

    private final PublishSubject<Observable<ChangeNotification<InstanceInfo>>> upgrades = PublishSubject.create();
    private final Observable<ChangeNotification<InstanceInfo>> aggregatedStream = Observable.merge(upgrades);

    public InterestNotificationMultiplexer(EurekaRegistry<InstanceInfo> eurekaRegistry) {
        this.eurekaRegistry = eurekaRegistry;
    }

    /**
     * For composite interest, we flatten it first, and than make parallel subscriptions to the registry.
     */
    public void update(Interest<InstanceInfo> newInterest) {
        if (newInterest instanceof MultipleInterests) {
            Set<Interest<InstanceInfo>> newInterestSet = ((MultipleInterests<InstanceInfo>) newInterest).flatten();
            // Remove interests not present in the new subscription
            Set<Interest<InstanceInfo>> toRemove = new HashSet<>(subscriptions.keySet());
            toRemove.removeAll(newInterestSet);
            for (Interest<InstanceInfo> item : toRemove) {
                removeInterest(item);
            }
            // Add new interests
            Set<Interest<InstanceInfo>> toAdd = new HashSet<>(newInterestSet);
            toAdd.removeAll(subscriptions.keySet());
            for (Interest<InstanceInfo> item : toAdd) {
                subscribeToInterest(item);
            }
        } else {
            // First remove all interests except the current one if present
            Set<Interest<InstanceInfo>> toRemove = new HashSet<>(subscriptions.keySet());
            toRemove.remove(newInterest);
            for (Interest<InstanceInfo> item : toRemove) {
                removeInterest(item);
            }
            // Add new interests
            if (subscriptions.isEmpty()) {
                subscribeToInterest(newInterest);
            }
        }
    }

    /**
     * To be able to complete the notification stream on interest upgrade with remove we use
     * {@link rx.subjects.PublishSubject} as a bridge. When a given interest should be removed,
     * the corresponding {@link rx.subjects.PublishSubject} is completed.
     */
    private void subscribeToInterest(Interest<InstanceInfo> newInterest) {
        PublishSubject<ChangeNotification<InstanceInfo>> publishSubject = PublishSubject.create();

        ConnectableObservable<ChangeNotification<InstanceInfo>> connectableObservable = publishSubject.publish();
        upgrades.onNext(connectableObservable);

        eurekaRegistry.forInterest(newInterest).subscribe(publishSubject);
        connectableObservable.connect();

        subscriptions.put(newInterest, publishSubject);
    }

    private void removeInterest(Interest<InstanceInfo> currentInterest) {
        subscriptions.remove(currentInterest).onCompleted();
    }

    public void unregister() {
        for (PublishSubject<ChangeNotification<InstanceInfo>> subject : subscriptions.values()) {
            subject.onCompleted();
        }
        subscriptions.clear();
    }

    /**
     * Interest channel creates a single subscription to this observable prior to
     * registering any interest set. We can safely use hot observable, which
     * simplifies implementation of the multiplexer.
     */
    public Observable<ChangeNotification<InstanceInfo>> changeNotifications() {
        return aggregatedStream;
    }
}
