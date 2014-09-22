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

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka.client.EurekaClient;
import com.netflix.eureka.client.ServerResolver;
import rx.Observable;
import rx.Observable.Operator;
import rx.Subscriber;
import rx.functions.Action1;
import rx.subjects.PublishSubject;

/**
 * {@link ServerResolver} implementations do not cache data. {@link BufferedServerResolver} is a wrapper
 * which adds buffering feature. Its main benefit is however in making it possible to setup conditions
 * on server list content, which can be used to delay construction of objects depending on whats in the server list.
 * For example creation of {@link EurekaClient} with empty server pool, will most likely result
 * in an excpetion from an underlying Ribbon load balancer.
 *
 * @author Tomasz Bak
 */
public class BufferedServerResolver<A extends SocketAddress> implements ServerResolver<A> {

    private final ServerResolver<A> delegate;
    private final ConcurrentHashMap<A, ServerEntry<A>> currentServers = new ConcurrentHashMap<>();

    private final PublishSubject<ServerEntry<A>> liveStream = PublishSubject.create();

    public BufferedServerResolver(ServerResolver<A> delegate) {
        this.delegate = delegate;
        delegate.resolve().subscribe(new Action1<ServerEntry<A>>() {
            @Override
            public void call(ServerEntry<A> serverEntry) {
                switch (serverEntry.getAction()) {
                    case Add:
                        currentServers.put(serverEntry.getServer(), serverEntry);
                        break;
                    case Remove:
                        currentServers.remove(serverEntry.getServer());
                        break;
                }
                liveStream.onNext(serverEntry);
            }
        });
    }

    public List<ServerEntry<A>> currentSnapshot() {
        return new ArrayList<>(currentServers.values());
    }

    @Override
    public Observable<ServerEntry<A>> resolve() {
        // TODO: this is simplistic implementation prone to races.
        return Observable.concat(
                Observable.from(currentServers.values()),
                liveStream
        );
    }

    @Override
    public void start() {
    }

    @Override
    public void close() {
        delegate.close();
    }

    public static Observable<Void> completeOnPoolSize(final BufferedServerResolver<?> serverResolver, final int minDesiredSize, long timeout, TimeUnit timeUnit) {
        if (serverResolver.currentSnapshot().size() >= minDesiredSize) {
            return Observable.empty();
        }
        return serverResolver.resolve().lift(new Operator<Void, ServerEntry<?>>() {
            @Override
            public Subscriber<? super ServerEntry<?>> call(final Subscriber<? super Void> subscriber) {
                return new Subscriber<ServerEntry<?>>() {
                    @Override
                    public void onCompleted() {
                        subscriber.onCompleted();
                    }

                    @Override
                    public void onError(Throwable e) {
                        subscriber.onError(e);
                    }

                    @Override
                    public void onNext(ServerEntry<?> serverEntry) {
                        if (serverResolver.currentSnapshot().size() >= minDesiredSize) {
                            subscriber.onCompleted();
                        }
                    }
                };
            }
        }).timeout(timeout, timeUnit);
    }
}
