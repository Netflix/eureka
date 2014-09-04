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

package com.netflix.eureka.client.transport.http;


import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import rx.Observable.Operator;
import rx.Subscriber;

/**
 * @author Tomasz Bak
 */
public class NoContentComplete<T> implements Operator<HttpClientResponse<T>, HttpClientResponse<T>> {
    @Override
    public Subscriber<HttpClientResponse<T>> call(final Subscriber<? super HttpClientResponse<T>> subscriber) {
        return new Subscriber<HttpClientResponse<T>>() {
            @Override
            public void onCompleted() {
                subscriber.onCompleted();
            }

            @Override
            public void onError(Throwable e) {
                subscriber.onError(e);
            }

            @Override
            public void onNext(HttpClientResponse<T> response) {
                subscriber.onCompleted();
            }
        };
    }
}
