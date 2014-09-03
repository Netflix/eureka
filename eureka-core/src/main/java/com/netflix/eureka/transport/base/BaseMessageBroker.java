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

package com.netflix.eureka.transport.base;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

import com.netflix.eureka.transport.Acknowledgement;
import com.netflix.eureka.transport.MessageBroker;
import io.reactivex.netty.channel.ObservableConnection;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.subjects.PublishSubject;
import rx.subjects.ReplaySubject;

/**
 * @author Tomasz Bak
 */
public class BaseMessageBroker implements MessageBroker {

    private final ObservableConnection<Object, Object> connection;
    private final PublishSubject<Void> lifecycleSubject = PublishSubject.create();

    private final Queue<ReplaySubject<Void>> pendingAck = new ConcurrentLinkedQueue<>();

    public BaseMessageBroker(ObservableConnection<Object, Object> connection) {
        this.connection = connection;
        installAcknowledgementHandler();
    }

    private void installAcknowledgementHandler() {
        connection.getInput().subscribe(new Action1<Object>() {
            @Override
            public void call(Object message) {
                if (!(message instanceof Acknowledgement)) {
                    return;
                }
                if (pendingAck.isEmpty()) {
                    lifecycleSubject.onError(new IllegalStateException("received acknowledgment while non expected"));
                    return;
                }
                ReplaySubject<Void> observable = pendingAck.poll();
                observable.onCompleted();
            }
        });
    }

    @Override
    public Observable<Void> submit(Object message) {
        return writeWhenSubscribed(message);
    }

    @Override
    public Observable<Void> submitWithAck(Object message) {
        return submitWithAck(message, 0);
    }

    @Override
    public Observable<Void> submitWithAck(Object message, long timeout) {
        ReplaySubject<Void> ackObservable = ReplaySubject.create();
        pendingAck.add(ackObservable);

        return Observable.concat(
                writeWhenSubscribed(message),
                ackObservable
        );
    }

    @Override
    public Observable<Void> acknowledge(Object message) {
        return writeWhenSubscribed(new Acknowledgement(""));
    }

    @Override
    public Observable<Object> incoming() {
        return connection.getInput().filter(new Func1<Object, Boolean>() {
            @Override
            public Boolean call(Object message) {
                return !(message instanceof Acknowledgement);
            }
        });
    }

    @Override
    public void shutdown() {
        Observable<Void> closeObservable = connection.close();
        closeObservable.subscribe(lifecycleSubject);
    }

    @Override
    public Observable<Void> lifecycleObservable() {
        return lifecycleSubject;
    }

    // TODO: can we optimize that?
    private Observable<Void> writeWhenSubscribed(final Object message) {
        final AtomicReference<Observable<Void>> observableRef = new AtomicReference<>();
        return Observable.create(new Observable.OnSubscribe<Void>() {
            @Override
            public void call(Subscriber<? super Void> subscriber) {
                synchronized (observableRef) {
                    if (observableRef.get() == null) {
                        observableRef.set(connection.writeAndFlush(message));
                    }
                }
                observableRef.get().subscribe(subscriber);
            }
        });
    }
}
