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

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Scheduler;
import rx.Scheduler.Worker;
import rx.Subscriber;
import rx.functions.Action0;
import rx.subjects.ReplaySubject;

/**
 * An abstract service channel with reconnect capabilities. It implements exponential back off
 * to avoid retry storm. It is used as a base by higher level {@link RetryableStatelessServiceChannel} and
 * {@link RetryableStatefullServiceChannel} implementations.
 *
 * @author Tomasz Bak
 */
public abstract class RetryableServiceChannel<C extends ServiceChannel> implements ServiceChannel {

    private static final Logger logger = LoggerFactory.getLogger(RetryableServiceChannel.class);

    public static final int MAX_EXP_BACK_OFF_MULTIPLIER = 10;

    // Channel descriptive name to be used in the log file - that should come from channel API
    protected final String name = getClass().getSimpleName();

    private final long retryInitialDelayMs;
    private final long maxRetryDelayMs;
    private final Worker worker;

    private long lastConnectTime;
    private long retryDelay;

    protected volatile boolean shutdown;

    private final ReplaySubject<Void> lifecycleSubject = ReplaySubject.create();

    protected RetryableServiceChannel(long retryInitialDelayMs, Scheduler scheduler) {
        this.retryInitialDelayMs = retryInitialDelayMs;
        this.maxRetryDelayMs = retryInitialDelayMs * MAX_EXP_BACK_OFF_MULTIPLIER;
        this.worker = scheduler.createWorker();
        this.retryDelay = retryInitialDelayMs;
    }

    @Override
    public void close() {
        if (!shutdown) {
            shutdown = true;
            worker.unsubscribe();
        }
    }

    @Override
    public Observable<Void> asLifecycleObservable() {
        return lifecycleSubject;
    }

    protected Worker getWorker() {
        return worker;
    }

    protected abstract void retry();

    protected boolean recoverableError(Throwable error) {
        return true;
    }

    protected void scheduleRetry() {
        worker.schedule(retryAction, retryDelay, TimeUnit.MILLISECONDS);
        bumpUpRetryDelay();
    }

    protected void subscribeToChannelLifecycle(C channelDelegate) {
        final Observable<Void> lifecycleObservable = channelDelegate.asLifecycleObservable();

        lifecycleObservable.subscribe(new Subscriber<Void>() {
            @Override
            public void onCompleted() {
                if (!shutdown) {
                    logger.info("Channel closed gracefully and must be reconnected");
                    long closedAfter = worker.now() - lastConnectTime;
                    if (closedAfter >= maxRetryDelayMs) {
                        retryDelay = retryInitialDelayMs;
                    }
                    scheduleRetry();
                }
            }

            @Override
            public void onError(Throwable e) {
                if (!shutdown) {
                    if (recoverableError(e)) {
                        logger.info("Channel failure; scheduling the reconnection in " + retryDelay + "ms", e);
                        scheduleRetry();
                    } else {
                        logger.error("Unrecoverable error; closing the retryable channel");
                        lifecycleSubject.onError(e);
                        close();
                    }
                }
            }

            @Override
            public void onNext(Void aVoid) {
                // No-op
            }
        });
    }

    private void bumpUpRetryDelay() {
        retryDelay = Math.min(maxRetryDelayMs, retryDelay * 2);
    }

    private final Action0 retryAction = new Action0() {
        @Override
        public void call() {
            if (shutdown) {
                return;
            }
            lastConnectTime = worker.now();
            retry();
        }
    };
}
