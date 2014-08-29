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

package com.netflix.eureka.client.service;

import com.netflix.eureka.client.transport.CommunicationFailure;
import com.netflix.eureka.client.transport.CommunicationFailure.Reason;
import com.netflix.eureka.datastore.Item;
import com.netflix.eureka.interests.ChangeNotification;
import com.netflix.eureka.interests.Interest;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.service.InterestChannel;
import rx.Observable;

/**
 * @author Tomasz Bak
 */
public class UnavailableInterestChannel implements InterestChannel {

    public static final InterestChannel INSTANCE = new UnavailableInterestChannel();

    protected UnavailableInterestChannel() {
    }

    @Override
    public Observable<Void> upgrade(Interest<InstanceInfo> newInterest) {
        return Observable.error(new CommunicationFailure("Communication with Eureka server not established", Reason.Temporary));
    }

    @Override
    public Observable<ChangeNotification<? extends Item>> asObservable() {
        return Observable.error(new CommunicationFailure("Communication with Eureka server not established", Reason.Temporary));
    }

    @Override
    public void heartbeat() {
    }

    @Override
    public void close() {
    }

    @Override
    public Observable<Void> asLifecycleObservable() {
        return Observable.error(new CommunicationFailure("Communication with Eureka server not established", Reason.Temporary));
    }
}
