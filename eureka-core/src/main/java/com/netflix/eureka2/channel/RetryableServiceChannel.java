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

package com.netflix.eureka2.channel;

import com.netflix.eureka2.channel.RetryableServiceChannel.STATES;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Scheduler;
import rx.Scheduler.Worker;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Action0;

/**
 * An abstract service channel decorator with reconnect capabilities. It implements exponential back off
 * to avoid retry storm. Note that the RetryableServiceChannel's lifecycle is independent of the delegate
 * channel lifecycles.
 *
 * @author Tomasz Bak
 */
public abstract class RetryableServiceChannel<C extends ServiceChannel> extends AbstractServiceChannel<STATES> {

    private static final Logger logger = LoggerFactory.getLogger(RetryableServiceChannel.class);

    public static final int MAX_EXP_BACK_OFF_MULTIPLIER = 10;

    public enum STATES {Open, Closed}

    private final AtomicReference<C> currentChannelRef;  // store current active channel
    private AtomicReference<Subscription> delegateLifecycleSubscription;

    private final long retryInitialDelayMs;
    private final long maxRetryDelayMs;
    private final Worker worker;

    private long lastConnectTime;
    private long retryDelay;

    protected RetryableServiceChannel(C initialDelegate, long retryInitialDelayMs, Scheduler scheduler) {
        super(STATES.Open);

        this.currentChannelRef = new AtomicReference<>(initialDelegate);
        this.delegateLifecycleSubscription = new AtomicReference<>(null);

        this.retryInitialDelayMs = retryInitialDelayMs;
        this.maxRetryDelayMs = retryInitialDelayMs * MAX_EXP_BACK_OFF_MULTIPLIER;
        this.worker = scheduler.createWorker();
        this.retryDelay = retryInitialDelayMs;

        subscribeToDelegateChannelLifecycle(initialDelegate);
    }

    /**
     * Note that calling close() on the retryableChannelDecorator will stop all further retries
     */
    @Override
    protected void _close() {
        if (state.get() != STATES.Closed) {
            worker.unsubscribe();

            Subscription currentSubscription = delegateLifecycleSubscription.get();
            if (currentSubscription != null && !currentSubscription.isUnsubscribed()) {
                currentSubscription.unsubscribe();
            }

            C delegateChannel = currentChannelRef.get();
            if (delegateChannel != null) {
                delegateChannel.close();
            }

            state.compareAndSet(STATES.Open, STATES.Closed);
        }
    }

    protected C currentDelegateChannel() {
        return currentChannelRef.get();
    }

    protected Observable<C> currentDelegateChannelObservable() {
        return Observable.create(new Observable.OnSubscribe<C>() {
            @Override
            public void call(Subscriber<? super C> subscriber) {
                try {
                    subscriber.onNext(currentChannelRef.get());
                    subscriber.onCompleted();
                } catch (Exception e) {
                    subscriber.onError(e);
                }
            }
        });
    }

    /**
     * Implement to return a new delegate channel for retry purposes. This new channel should be
     * "warmed up" before emit if necessary.
     * @return an observable that emits a new, "warmed up" delegate channel, then completes
     */
    protected abstract Observable<C> reestablish();

    protected void retry() {
        logger.info("Retrying ...");
        reestablish().single().subscribe(new Subscriber<C>() {
            @Override
            public void onCompleted() {
            }

            @Override
            public void onError(Throwable e) {
                scheduleRetry();
            }

            @Override
            public void onNext(C newDelegateChannel) {
                // first switch to the new delegate, then close the old delegate
                C oldDelegateChannel = currentChannelRef.getAndSet(newDelegateChannel);
                subscribeToDelegateChannelLifecycle(newDelegateChannel);

                if (oldDelegateChannel != null) {
                    oldDelegateChannel.close();
                }
            }
        });
    }

    /**
     * @Override for specific Retryable service channels if they need to implement more fine grained logic
     */
    protected boolean recoverableError(Throwable error) {
        return true;
    }

    protected void scheduleRetry() {
        long closedAfter = worker.now() - lastConnectTime;
        if (closedAfter >= maxRetryDelayMs) {
            retryDelay = retryInitialDelayMs;
        }

        worker.schedule(retryAction, retryDelay, TimeUnit.MILLISECONDS);
        bumpUpRetryDelay();
    }

    protected void subscribeToDelegateChannelLifecycle(C newDelegateChannel) {
        final Observable<Void> lifecycleObservable = newDelegateChannel.asLifecycleObservable();

        Subscription oldSubscription = delegateLifecycleSubscription.getAndSet(
                lifecycleObservable.subscribe(new Subscriber<Void>() {
                    @Override
                    public void onCompleted() {
                        logger.info("Channel closed gracefully and must be reconnected");
                        scheduleRetry();
                    }

                    @Override
                    public void onError(Throwable e) {
                        if (recoverableError(e)) {
                            logger.info("Channel failure; scheduling the reconnection in " + retryDelay + "ms", e);
                            scheduleRetry();
                        } else {
                            logger.error("Unrecoverable error; closing the retryable channel");
                            lifecycle.onError(e);
                            close();
                        }
                    }

                    @Override
                    public void onNext(Void aVoid) {
                        // No-op
                    }
                })
        );

        if (oldSubscription != null && !oldSubscription.isUnsubscribed()) {
            oldSubscription.unsubscribe();
        }
    }

    private void bumpUpRetryDelay() {
        retryDelay = Math.min(maxRetryDelayMs, retryDelay * 2);
    }

    private final Action0 retryAction = new Action0() {
        @Override
        public void call() {
            lastConnectTime = worker.now();
            retry();
        }
    };

}
