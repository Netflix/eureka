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

import java.net.SocketAddress;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import com.netflix.eureka2.metric.MessageConnectionMetrics;
import com.netflix.eureka2.transport.Acknowledgement;
import com.netflix.eureka2.transport.MessageConnection;
import io.reactivex.netty.channel.ObservableConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Scheduler;
import rx.Scheduler.Worker;
import rx.Subscriber;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;
import rx.subjects.AsyncSubject;
import rx.subjects.SerializedSubject;
import rx.subjects.Subject;

/**
 * @author Tomasz Bak
 */
public class BaseMessageConnection implements MessageConnection {

    private static final Logger logger = LoggerFactory.getLogger(BaseMessageConnection.class);

    private final String name;
    private final ObservableConnection<Object, Object> connection;
    private final MessageConnectionMetrics metrics;
    private final Worker schedulerWorker;
    private final long startTime;
    private final AtomicBoolean closed = new AtomicBoolean();

    private final Subject<Void, Void> lifecycleSubject = new SerializedSubject<>(AsyncSubject.<Void>create());

    private final Queue<PendingAck> pendingAckQueue = new ConcurrentLinkedQueue<>();

    /**
     * If latest acknowledgement exceeds timeout value, discard this connection.
     * Such an event indicates that the other side is not responsive at the application level, and
     * continuing sending more messages has no point, and may result in large memory pressure due to
     * data accumulation in buffers, including {@link #pendingAckQueue} queue.
     */
    private final Action0 ackTimeoutTask = new Action0() {
        @Override
        public void call() {
            try {
                long currentTime = schedulerWorker.now();
                PendingAck latestAck = pendingAckQueue.peek();
                if (latestAck != null && latestAck.getExpiryTime() <= currentTime) {
                    latestAck = pendingAckQueue.peek();
                    TimeoutException timeoutException = new TimeoutException("{connection=" + name + "}: acknowledgement timeout");
                    // Only item that caused the timeout will receive timeout exception
                    // In shutdown -> drainPendingAckQueue we onComplete pending acks to avoid excessive noise
                    // This is not easy to fix with current implementation, and should be handled during planned
                    // transport refactoring.
                    if (latestAck != null) {
                        latestAck.onError(timeoutException);
                    }
                    doShutdown(timeoutException);
                } else {
                    schedulerWorker.schedule(ackTimeoutTask, 1, TimeUnit.SECONDS);
                }
            } catch (Exception e) {
                logger.error("Acknowledgement cleanup task failed with an exception", e);
                doShutdown(e);
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

        this.startTime = expiryScheduler.now();
        metrics.incrementConnectionCounter();
    }

    private String descriptiveName(String name) {
        SocketAddress remoteAddress = connection.getChannel().remoteAddress();
        String endpointName = remoteAddress == null ? "<no-remote>" : remoteAddress.toString();
        return name + "=>" + endpointName;
    }

    private void installAcknowledgementHandler() {
        connection.getInput()
                .ofType(Acknowledgement.class)
                .subscribe(new Action1<Acknowledgement>() {
                    @Override
                    public void call(Acknowledgement acknowledgement) {
                        PendingAck pending = pendingAckQueue.poll();
                        metrics.decrementPendingAckCounter();
                        if (pending == null) {
                            shutdown(new IllegalStateException("{connection=" + name + "}: unexpected acknowledgment"));
                        } else {
                            pending.ackSubject.onCompleted();
                        }
                    }
                });

        schedulerWorker.schedule(ackTimeoutTask, 1, TimeUnit.SECONDS);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Observable<Void> submit(Object message) {
        if (closed.get()) {
            return Observable.error(new IllegalStateException("{connection=" + name + "}: connection closed"));
        }
        return writeWhenSubscribed(message);
    }

    @Override
    public Observable<Void> submitWithAck(Object message) {
        return submitWithAck(message, 0);
    }

    @Override
    public Observable<Void> submitWithAck(final Object message, final long timeout) {
        if (closed.get()) {
            return Observable.error(new IllegalStateException("{connection=" + name + "}: connection closed"));
        }
        long expiryTime = timeout <= 0 ? Long.MAX_VALUE : schedulerWorker.now() + timeout;
        return writeWhenSubscribed(message, PendingAck.create(expiryTime));
    }

    @Override
    public Observable<Void> acknowledge() {
        if (closed.get()) {
            return Observable.error(new IllegalStateException("{connection=" + name + "}: connection closed"));
        }
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
        }).doOnTerminate(new Action0() {
            @Override
            public void call() {
                // always close with an exception here as the underlying connection never onError
                shutdown(new IllegalStateException("{connection=" + name + "}: connection input onCompleted"));
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
        doShutdown(null);
    }

    @Override
    public void shutdown(final Throwable e) {
        doShutdown(e);
    }

    private void doShutdown(final Throwable throwable) {
        boolean wasClosed = closed.getAndSet(true);
        if (!wasClosed) {
            if (throwable == null) {
                logger.info("Shutting down connection {}", name);
            } else {
                logger.info("Shutting down connection {} because of an exception {}:{}", name, throwable.getClass().getName(), throwable.getMessage());
            }
            drainPendingAckQueue();

            metrics.decrementConnectionCounter();
            metrics.connectionDuration(startTime);

            // Change lifecycle state before closing connection so subscribers can do proper cleanup
            terminateLifecycle(throwable);

            // We do not care about connection close result
            connection.close()
                    .subscribe(new Subscriber<Void>() {
                        @Override
                        public void onCompleted() {
                        }

                        @Override
                        public void onError(Throwable e) {
                            logger.debug("Error during closing the connection", e);
                        }

                        @Override
                        public void onNext(Void aVoid) {
                            // No-op
                        }
                    });

            schedulerWorker.unsubscribe();
        }
    }

    /**
     * This method is thread safe, and it may be called concurrently.
     */
    private void drainPendingAckQueue() {
        PendingAck pendingAck;
        while ((pendingAck = pendingAckQueue.poll()) != null) {
            metrics.decrementPendingAckCounter();
            try {
                /**
                 * TODO Although send onError here is what we should do, in current code it produces a lot misleading noise.
                 */
                pendingAck.onCompleted();
//                pendingAck.onError(createException(CancellationException.class, "request cancelled"));
            } catch (Exception e) {
                logger.warn("Acknowledgement subscriber hasn't handled properly onError", e);
            }
        }
    }

    private void terminateLifecycle(Throwable e) {
        if (e == null) {
            lifecycleSubject.onCompleted();
        } else {
            lifecycleSubject.onError(e);
        }
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
        return connection
                .writeAndFlush(message)
                .doOnSubscribe(new Action0() {
                    @Override
                    public void call() {
                        pendingAckQueue.add(ack);
                        metrics.incrementPendingAckCounter();
                        metrics.incrementOutgoingMessageCounter(message.getClass(), 1);
                    }
                })
                .doOnCompleted(new Action0() {
                    @Override
                    public void call() {
                        // Connection might be closed when we serve this request, and since
                        // pendingAckQueue and closed variables are not changed together atomically, we check
                        // close status after adding item to pendingAckQueue, and optionally drain it.
                        if (closed.get()) {
                            drainPendingAckQueue();
                        }
                    }
                })
                .concatWith(ack)
                .cache();
    }

    /**
     * A special subject that that deals with independent updates from input stream and expiry task.
     */
    static class PendingAck extends Subject<Void, Void> {
        private final long expiryTime;
        private final Subject<Void, Void> ackSubject;
        private final AtomicBoolean isCompleted = new AtomicBoolean();

        PendingAck(OnSubscribe<Void> onSubscribe, Subject<Void, Void> ackSubject, long expiryTime) {
            super(onSubscribe);
            this.ackSubject = ackSubject;
            this.expiryTime = expiryTime;
        }

        public long getExpiryTime() {
            return expiryTime;
        }

        @Override
        public boolean hasObservers() {
            return ackSubject.hasObservers();
        }

        @Override
        public void onCompleted() {
            if (isCompleted.compareAndSet(false, true)) {
                ackSubject.onCompleted();
            }
        }

        @Override
        public void onError(Throwable e) {
            if (isCompleted.compareAndSet(false, true)) {
                ackSubject.onError(e);
            }
        }

        @Override
        public void onNext(Void aVoid) {
            // No-op
        }

        static PendingAck create(long expiryTime) {
            final Subject<Void, Void> ackSubject = AsyncSubject.create();
            OnSubscribe<Void> onSubscribe = new OnSubscribe<Void>() {
                @Override
                public void call(Subscriber<? super Void> subscriber) {
                    ackSubject.subscribe(subscriber);
                }
            };
            return new PendingAck(onSubscribe, ackSubject, expiryTime);
        }
    }
}
