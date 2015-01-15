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

package com.netflix.eureka2.transport.base;

import com.netflix.eureka2.metric.MessageConnectionMetrics;
import com.netflix.eureka2.transport.Acknowledgement;
import com.netflix.eureka2.transport.MessageConnection;
import io.reactivex.netty.channel.ObservableConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Scheduler;
import rx.Scheduler.Worker;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;
import rx.subjects.ReplaySubject;
import rx.subjects.Subject;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Tomasz Bak
 */
public class BaseMessageConnection implements MessageConnection {

    private static final Logger logger = LoggerFactory.getLogger(BaseMessageConnection.class);

    private static final Pattern NETTY_CHANNEL_NAME_RE = Pattern.compile("\\[.*=>\\s*(.*)\\]");

    private static final Exception ACKNOWLEDGEMENT_TIMEOUT_EXCEPTION = new TimeoutException("acknowledgement timeout");
    private static final Exception UNEXPECTED_ACKNOWLEDGEMENT_EXCEPTION = new IllegalStateException("received acknowledgment while non expected");

    private final String name;
    private final ObservableConnection<Object, Object> connection;
    private final MessageConnectionMetrics metrics;
    private final Worker schedulerWorker;
    private final long startTime;

    private final PublishSubject<Void> lifecycleSubject = PublishSubject.create();

    private final Queue<PendingAck> pendingAck = new ConcurrentLinkedQueue<>();

    // FIXME this is not used now, may have race conditions with the acknowledgement handler so fix before enable
    private final Action0 cleanupTask = new Action0() {
        @Override
        public void call() {
            try {
                long currentTime = schedulerWorker.now();
                if (!pendingAck.isEmpty() && pendingAck.peek().getExpiryTime() <= currentTime) {
                    while (!pendingAck.isEmpty()) {
                        Subject<Void, Void> ackSubject = pendingAck.poll().getAckSubject();
                        ackSubject.onError(ACKNOWLEDGEMENT_TIMEOUT_EXCEPTION);
                    }
                    lifecycleSubject.onError(ACKNOWLEDGEMENT_TIMEOUT_EXCEPTION);
                } else {
                    schedulerWorker.schedule(cleanupTask, 1, TimeUnit.SECONDS);
                }
            } catch (RuntimeException e) {
                logger.error("Acknowledgement cleanup task failed with an exception: " + e.getMessage());
                logger.debug("Acknowledgement failure stack trace", e);
                throw e;
            }
        }
    };

    public BaseMessageConnection(
            String name,
            ObservableConnection<Object, Object> connection,
            MessageConnectionMetrics metrics) {
        this(name, connection, metrics, Schedulers.computation());
    }

    public BaseMessageConnection(
            String name,
            ObservableConnection<Object, Object> connection,
            MessageConnectionMetrics metrics,
            Scheduler expiryScheduler) {
        this.connection = connection;
        this.metrics = metrics;
        this.name = descriptiveName(name);
        schedulerWorker = expiryScheduler.createWorker();
        installAcknowledgementHandler();

        this.startTime = System.currentTimeMillis();
        metrics.incrementConnectedClients();
    }

    private String descriptiveName(String name) {
        String endpointName = connection.getChannel().toString();
        Matcher matcher = NETTY_CHANNEL_NAME_RE.matcher(endpointName);
        if (matcher.matches()) {
            endpointName = matcher.group(1);
        }
        return name + "=>" + endpointName;
    }

    private void installAcknowledgementHandler() {
        connection.getInput()
                .ofType(Acknowledgement.class)
                .subscribe(new Action1<Acknowledgement>() {
                    @Override
                    public void call(Acknowledgement acknowledgement) {
                        PendingAck pending = pendingAck.poll();
                        if (pending == null) {
                            lifecycleSubject.onError(UNEXPECTED_ACKNOWLEDGEMENT_EXCEPTION);
                        } else {
                            pending.ackSubject.onCompleted();
                        }
                    }
                });

        schedulerWorker.schedule(cleanupTask, 1, TimeUnit.SECONDS);
    }

    @Override
    public String name() {
        return name;
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
    public Observable<Void> submitWithAck(final Object message, final long timeout) {
        long expiryTime = timeout <= 0 ? Long.MAX_VALUE : schedulerWorker.now() + timeout;

        return writeWhenSubscribed(message, new PendingAck(expiryTime));
    }

    @Override
    public Observable<Void> acknowledge() {
        return writeWhenSubscribed(Acknowledgement.INSTANCE);
    }

    // TODO: Return always the same observable
    @Override
    public Observable<Object> incoming() {
        return connection.getInput().filter(new Func1<Object, Boolean>() {
            @Override
            public Boolean call(Object message) {
                return !(message instanceof Acknowledgement);
            }
        }).doOnNext(new Action1<Object>() {
            @Override
            public void call(Object o) {
                metrics.incrementIncomingMessageCounter(o.getClass(), 1);
            }
        });
    }

    @Override
    public Observable<Void> onError(Throwable error) {
        return Observable.error(error);
    }

    @Override
    public Observable<Void> onCompleted() {
        return Observable.empty();
    }

    @Override
    public void shutdown() {
        metrics.decrementConnectedClients();
        metrics.clientConnectionTime(startTime);

        Observable<Void> closeObservable = connection.close();
        closeObservable.subscribe(lifecycleSubject);
        schedulerWorker.unsubscribe();
    }

    @Override
    public Observable<Void> lifecycleObservable() {
        return lifecycleSubject;
    }

    private Observable<Void> writeWhenSubscribed(final Object message) {
        return connection.writeAndFlush(message)
                .doOnCompleted(new Action0() {
                    @Override
                    public void call() {
                        metrics.incrementOutgoingMessageCounter(message.getClass(), 1);
                    }
                })
                .cache();
    }

    private Observable<Void> writeWhenSubscribed(final Object message, final PendingAck ack) {
        return connection.writeAndFlush(message)
                .doOnCompleted(new Action0() {
                    @Override
                    public void call() {
                        pendingAck.add(ack);
                        metrics.incrementOutgoingMessageCounter(message.getClass(), 1);
                    }
                })
                .concatWith(ack.getAckSubject())
                .cache();
    }

    static class PendingAck {
        private final long expiryTime;
        private final Subject<Void, Void> ackSubject;

        PendingAck(long expiryTime) {
            this.expiryTime = expiryTime;
            this.ackSubject = ReplaySubject.create();
        }

        public long getExpiryTime() {
            return expiryTime;
        }

        public Subject<Void, Void> getAckSubject() {
            return ackSubject;
        }
    }
}
