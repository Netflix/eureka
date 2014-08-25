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

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.service.RegistrationChannel;
import rx.Observable;
import rx.subjects.PublishSubject;

/**
 * @author Tomasz Bak
 */
public class TestableRegistrationChannel implements RegistrationChannel {

    private final BlockingQueue<Object> updateQueue = new LinkedBlockingQueue<Object>();

    private final BlockingQueue<Long> heartbeatsQueue = new LinkedBlockingQueue<Long>();

    private final PublishSubject<Void> closeObservable = PublishSubject.create();

    @Override
    public Observable<Void> register(InstanceInfo newInfo) {
        return null;
    }

    @Override
    public Observable<Void> update(InstanceInfo newInfo) {
        updateQueue.add(newInfo);
        return Observable.empty();
    }

    @Override
    public void heartbeat() {
        heartbeatsQueue.add(System.currentTimeMillis());
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

    public BlockingQueue<Object> viewUpdates() {
        return updateQueue;
    }

    public BlockingQueue<Long> viewHeartbeats() {
        return heartbeatsQueue;
    }

    /**
     * Return observable that completes when the channel is closed.
     */
    public Observable<Void> viewClose() {
        return closeObservable;
    }
}
