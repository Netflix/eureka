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

package com.netflix.eureka2.client.channel.consumer;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.netflix.eureka2.service.ServiceChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Scheduler;
import rx.Scheduler.Worker;
import rx.Subscriber;
import rx.functions.Action0;

/**
 * @author Tomasz Bak
 */
public abstract class RetryableChannelConsumer<C extends ServiceChannel, S> {

    private static final Logger logger = LoggerFactory.getLogger(RetryableChannelConsumer.class);

    public static final int MAX_EXP_BACK_OFF_MULTIPLIER = 10;

    public class StateWithChannel {
        private final C channel;
        private final S state;

        public StateWithChannel(C channel, S state) {
            this.channel = channel;
            this.state = state;
        }

        public C getChannel() {
            return channel;
        }

        public S getState() {
            return state;
        }
    }

    private final long retryInitialDelayMs;
    private final long maxRetryDelayMs;
    private final Worker worker;
    private final AtomicReference<StateWithChannel> currentStateWithChannel = new AtomicReference<>();

    private long lastConnectTime;
    private long retryDelay;

    protected RetryableChannelConsumer(long retryInitialDelayMs, Scheduler scheduler) {
        this.retryInitialDelayMs = retryInitialDelayMs;
        this.maxRetryDelayMs = retryInitialDelayMs * MAX_EXP_BACK_OFF_MULTIPLIER;
        this.worker = scheduler.createWorker();
        this.retryDelay = retryInitialDelayMs;
    }

    public StateWithChannel getStateWithChannel() {
        return currentStateWithChannel.get();
    }

    public void shutdownRetryableConsumer() {
        worker.unsubscribe();
    }

    protected abstract StateWithChannel reestablish();

    protected abstract Observable<Void> repopulate(StateWithChannel newState);

    protected abstract void release(StateWithChannel oldState);

    protected void initializeRetryableConsumer() {
        currentStateWithChannel.set(reestablish());
        subscribeToChannelLifecycle();
    }

    private void retry() {
        final StateWithChannel newStateWithChannel = reestablish();
        lastConnectTime = worker.now();

        repopulate(newStateWithChannel).subscribe(new Subscriber<Void>() {
            @Override
            public void onCompleted() {
                StateWithChannel oldState = currentStateWithChannel.getAndSet(newStateWithChannel);
                subscribeToChannelLifecycle();
                release(oldState);
            }

            @Override
            public void onError(Throwable e) {
                scheduleRetry();
            }

            @Override
            public void onNext(Void aVoid) {
                // No-op
            }
        });
    }

    protected void scheduleRetry() {
        worker.schedule(retryAction, retryDelay, TimeUnit.MILLISECONDS);
        bumpUpRetryDelay();
    }

    private void bumpUpRetryDelay() {
        retryDelay = Math.min(maxRetryDelayMs, retryDelay * 2);
    }

    private void subscribeToChannelLifecycle() {
        Observable<Void> lifecycleObservable = getStateWithChannel().getChannel().asLifecycleObservable();
        lifecycleObservable.subscribe(new Subscriber<Void>() {
            @Override
            public void onCompleted() {
                logger.info("Interest channel closed gracefully and must be reconnected");
                long closedAfter = worker.now() - lastConnectTime;
                if (closedAfter >= maxRetryDelayMs) {
                    retryDelay = retryInitialDelayMs;
                }
                scheduleRetry();
            }

            @Override
            public void onError(Throwable e) {
                logger.info("Interest channel failure; reconnecting", e);
                scheduleRetry();
            }

            @Override
            public void onNext(Void aVoid) {
                // No-op
            }
        });
    }

    private final Action0 retryAction = new Action0() {
        @Override
        public void call() {
            retry();
        }
    };
}
