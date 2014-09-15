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

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashSet;
import java.util.concurrent.locks.ReentrantLock;

import com.netflix.eureka.client.ServerResolver;
import com.netflix.eureka.client.ServerResolver.ServerEntry.Action;
import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Subscriber;
import rx.observables.ConnectableObservable;
import rx.subjects.PublishSubject;

/**
 *
 * @author Tomasz Bak
 */
public class StaticServerResolver<A extends SocketAddress> implements ServerResolver<A> {

    private final HashSet<ServerEntry<A>> serverList = new HashSet<>();
    private final PublishSubject<ServerEntry<A>> serverPublisher = PublishSubject.create();
    private final ReentrantLock lock = new ReentrantLock(); // To enforce serialized observable updates in add/remove methods

    @SafeVarargs
    public StaticServerResolver(final A... serverList) {
        for (A server : serverList) {
            addServer(server, Protocol.Undefined);
        }
    }

    public void addServer(A server, Protocol protocol) {
        lock.lock();
        try {
            ServerEntry<A> entry = new ServerEntry<>(Action.Add, server, protocol);
            if (!serverList.contains(entry)) {
                serverPublisher.onNext(entry);
                serverList.add(entry);
            }
        } finally {
            lock.unlock();
        }
    }

    public void removeServer(A server) {
        lock.lock();
        try {
            for (ServerEntry<A> entry : serverList) {
                if (entry.getServer().equals(server)) {
                    serverList.remove(entry);
                    serverPublisher.onNext(new ServerEntry<A>(Action.Remove, server, entry.getProtocol()));
                }
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Observable<ServerEntry<A>> resolve() {
        return Observable.create(new OnSubscribe<ServerEntry<A>>() {
            @Override
            public void call(Subscriber<? super ServerEntry<A>> subscriber) {
                ConnectableObservable<ServerEntry<A>> entryObservable;
                HashSet<ServerEntry<A>> snapshot;
                lock.lock();
                try {
                    entryObservable = serverPublisher.publish();
                    snapshot = new HashSet<>(serverList);
                } finally {
                    lock.unlock();
                }
                for (ServerEntry<A> entry : snapshot) {
                    subscriber.onNext(entry);
                }
                entryObservable.subscribe(subscriber);
                entryObservable.connect();
            }
        });
    }

    @Override
    public void start() {
    }

    @Override
    public void close() {
    }

    public static StaticServerResolver<InetSocketAddress> hostResolver(String host, int port) {
        return new StaticServerResolver<InetSocketAddress>(new InetSocketAddress(host, port));
    }
}
