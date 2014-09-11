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

package com.netflix.eureka.utils.rx;

import com.netflix.eureka.interests.ChangeNotification;
import com.netflix.eureka.registry.InstanceInfo;
import rx.Observable.Operator;
import rx.Subscriber;
import rx.internal.util.SubscriptionList;

/**
 * An operator that allows one tracking subscriptions of all subscribers to a given observable.
 */
public class BreakerSwitchOperator implements Operator<ChangeNotification<InstanceInfo>, ChangeNotification<InstanceInfo>> {
    private final SubscriptionList subscriptions = new SubscriptionList();

    @Override
    public Subscriber<? super ChangeNotification<InstanceInfo>> call(final Subscriber<? super ChangeNotification<InstanceInfo>> subscriber) {
        subscriptions.add(subscriber);
        return subscriber;
    }

    public void close() {
        subscriptions.unsubscribe();
    }
}
