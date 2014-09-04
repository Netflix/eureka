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

package com.netflix.eureka.client.bootstrap;

import com.netflix.eureka.client.ServerResolver;
import rx.Observable;
import rx.Subscriber;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * @author Tomasz Bak
 */
public class StaticServerResolver<A extends SocketAddress> implements ServerResolver<A> {

    private final Observable<ServerEntry<A>> serverList;

    public StaticServerResolver(final A... serverList) {
        this.serverList = Observable.create(new Observable.OnSubscribe<ServerEntry<A>>() {
            @Override
            public void call(Subscriber<? super ServerEntry<A>> subscriber) {
                for (A server : serverList) {
                    subscriber.onNext(new ServerEntry<A>(ServerEntry.Action.Add, server));
                    // Never completes as this is a static list.
                }
            }
        });
    }

    @Override
    public Observable<ServerEntry<A>> resolve() {
        return serverList;
    }

    public static StaticServerResolver<InetSocketAddress> singleHostResolver(String host, int port) {
        return new StaticServerResolver<InetSocketAddress>(new InetSocketAddress(host, port));
    }
}
