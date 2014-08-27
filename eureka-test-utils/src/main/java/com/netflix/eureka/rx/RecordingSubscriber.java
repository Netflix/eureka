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

package com.netflix.eureka.rx;

import java.util.ArrayList;
import java.util.List;

import rx.Observable;
import rx.Subscriber;
import rx.Subscription;

/**
 * An observer that stores observable results and exposes them to a user.
 *
 * @author Tomasz Bak
 */
public class RecordingSubscriber<T> {

    private final Subscription subscription;

    private final List<T> itemList = new ArrayList<T>();
    private volatile boolean done;
    private volatile Throwable error;

    protected RecordingSubscriber(Observable<T> observable) {
        subscription = observable.subscribe(new Subscriber<T>() {
            @Override
            public void onCompleted() {
                done = true;
            }

            @Override
            public void onError(Throwable e) {
                done = true;
                error = e;
            }

            @Override
            public void onNext(T t) {
                synchronized (itemList) {
                    itemList.add(t);
                }
            }
        });
    }

    public Subscription getSubscription() {
        return subscription;
    }

    public boolean isDone() {
        return done;
    }

    public Throwable getError() {
        return error;
    }

    public List<T> items() {
        return new ArrayList<T>(itemList);
    }

    public static <T> RecordingSubscriber<T> subscribeTo(Observable<T> observable) {
        return new RecordingSubscriber<T>(observable);
    }
}
