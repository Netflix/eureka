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

import com.netflix.eureka.interests.ChangeNotification;
import com.netflix.eureka.interests.Interest;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.service.InterestChannel;
import rx.Observable;
import rx.subjects.PublishSubject;

/**
 * Stub implementation of {@link InterestChannel} suitable for unit testing.
 *
 * @author Tomasz Bak
 */
public class TestableInterestChannel implements InterestChannel {

    private PublishSubject<Void> closeObservable = PublishSubject.create();

    @Override
    public Observable<Void> upgrade(Interest<InstanceInfo> newInterest) {
        return null;
    }

    @Override
    public Observable<ChangeNotification<InstanceInfo>> asObservable() {
        return null;
    }

    @Override
    public void heartbeat() {

    }

    @Override
    public void close() {
        closeObservable.onCompleted();
    }

    @Override
    public Observable<Void> asLifecycleObservable() {
        return null;
    }

    /*
     * Test methods.
     */

    /**
     * Return observable that completes when the channel is closed.
     */
    public Observable<Void> viewClose() {
        return closeObservable;
    }
}
