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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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

    private final Map<String, ReplaySubject<Void>> pendingAck = new ConcurrentHashMap<String, ReplaySubject<Void>>();
    private final DelayQueue<AckExpiry> expiryQueue = new DelayQueue<AckExpiry>();
    private final ScheduledExecutorService expiryScheduler = Executors.newSingleThreadScheduledExecutor();

    private final Runnable cleanupTask = new Runnable() {
        @Override
        public void run() {
            try {
                while (!expiryQueue.isEmpty()) {
                    String correlationId = expiryQueue.poll().getCorrelationId();
                    ReplaySubject<Void> ackSubject = pendingAck.get(correlationId);
                    ackSubject.onError(new TimeoutException("acknowledgement timeout for message with correlation id " + correlationId));
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                expiryScheduler.schedule(cleanupTask, 1, TimeUnit.SECONDS);
            }
        }
    };

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
                Acknowledgement ack = (Acknowledgement) message;
                String correlationId = ack.getCorrelationId();
                ReplaySubject<Void> observable = pendingAck.get(correlationId);
                if (observable != null) {
                    observable.onCompleted();
                }
            }
        });
        expiryScheduler.schedule(cleanupTask, 1, TimeUnit.SECONDS);
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
        String correlationId = correlationIdFor(message);

        ReplaySubject<Void> ackObservable = ReplaySubject.create();
        pendingAck.put(correlationId, ackObservable);
        if (timeout > 0) {
            expiryQueue.put(new AckExpiry(correlationId, timeout));
        }

        return Observable.concat(
                writeWhenSubscribed(message),
                ackObservable
        );
    }

    @Override
    public Observable<Void> acknowledge(Object message) {
        return writeWhenSubscribed(new Acknowledgement(correlationIdFor(message)));
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
        expiryScheduler.shutdown();
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

    private String correlationIdFor(Object message) {
        return Integer.toString(message.hashCode());
    }

    private static class AckExpiry implements Delayed {
        private final String correlationId;
        private final long expiry;

        public AckExpiry(String correlationId, long timeout) {
            this.correlationId = correlationId;
            expiry = System.currentTimeMillis() + timeout;
        }

        public String getCorrelationId() {
            return correlationId;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            long delay = expiry - System.currentTimeMillis();
            return delay <= 0 ? 0 : unit.convert(delay, TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed o) {
            long d1 = getDelay(TimeUnit.MILLISECONDS);
            long d2 = o.getDelay(TimeUnit.MILLISECONDS);
            if (d1 < d2) {
                return -1;
            }
            if (d1 > d2) {
                return 1;
            }
            return 0;
        }
    }
}
