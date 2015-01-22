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

package com.netflix.eureka2.utils.rx;

import rx.Observable;
import rx.Observable.Operator;
import rx.Subscriber;
import rx.subjects.AsyncSubject;
import rx.subjects.Subject;

/**
 * An operator that allows one tracking subscriptions of all subscribers to a given observable.
 * When close() is called, this operator should onComplete to all subscribers and unsubscribe from the upstream;
 */
public class BreakerSwitchOperator<T> implements Operator<T, T> {

    private final Subject<Void, Void> onCompleteFuture = AsyncSubject.create();

    @Override
    public Subscriber<? super T> call(final Subscriber<? super T> subscriber) {
        return new BreakerSwitchSubscriber<>(subscriber, onCompleteFuture);
    }

    public void close() {
        onCompleteFuture.onCompleted();
    }

    private static class BreakerSwitchSubscriber<T> extends Subscriber<T> {

        private final Subscriber<T> actual;

        public BreakerSwitchSubscriber(Subscriber<T> actual, Observable<Void> onCompleteFuture) {
            super(actual);

            this.actual = actual;
            onCompleteFuture.subscribe(new Subscriber<Void>() {
                @Override
                public void onCompleted() {
                    BreakerSwitchSubscriber.this.unsubscribe();
                    BreakerSwitchSubscriber.this.onCompleted();
                }

                @Override
                public void onError(Throwable e) {
                }

                @Override
                public void onNext(Void aVoid) {
                }
            });
        }

        @Override
        public void onCompleted() {
            actual.onCompleted();
        }

        @Override
        public void onError(Throwable e) {
            actual.onError(e);
        }

        @Override
        public void onNext(T t) {
            actual.onNext(t);
        }
    }
}
