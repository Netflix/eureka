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

package com.netflix.eureka.transport.utils;


import java.util.concurrent.TimeUnit;

import com.netflix.eureka.transport.MessageBroker;
import rx.Observable;
import rx.Subscription;
import rx.functions.Func1;
import rx.subjects.PublishSubject;

/**
 * Client/server heartbeat implementation on top of {@link MessageBroker}.
 *
 * @author Tomasz Bak
 */
public class HeartBeatHandler {

    public abstract static class HeartbeatClient<T> {

        private final PublishSubject<Void> statusObservable = PublishSubject.create();
        private final Subscription heartbeatSubscription;

        protected HeartbeatClient(final MessageBroker clientBroker, long interval, TimeUnit unit) {
            heartbeatSubscription = Observable.interval(interval, unit).flatMap(new Func1<Long, Observable<Void>>() {
                @Override
                public Observable<Void> call(Long aLong) {
                    return clientBroker.submit(heartbeatMessage());
                }
            }).subscribe(statusObservable);
        }

        /**
         * Set on error if client/server communication is broken.
         */
        public Observable<Void> connectionStatus() {
            return statusObservable;
        }

        public void shutdown() {
            heartbeatSubscription.unsubscribe();
        }

        protected abstract T heartbeatMessage();
    }
}
