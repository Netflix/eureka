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

import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Scheduler;
import rx.Subscriber;

/**
 * Channel adapter that reconnects underlying channel in case of failures. It provides
 * a set of template methods/steps for the reconnect algorithm:
 * <ul>
 * <li>reestablish - create a new channel, parallel to the broken one</li>
 * <li>repopulate - re-establish the desired state from the new channel, prior to swapping it with the broken one</li>
 * <li>release - release all the resources allocated for the broken channel; this step is performed after the channel swap</li>
 * </ul>
 * After execution of these steps, the original channel is closed.
 *
 * @author Tomasz Bak
 */
public abstract class RetryableStatefullServiceChannel<C extends ServiceChannel, S> extends RetryableServiceChannel<C> {

    private static final Logger logger = LoggerFactory.getLogger(RetryableStatefullServiceChannel.class);

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

    private final AtomicReference<StateWithChannel> currentStateWithChannel = new AtomicReference<>();

    protected RetryableStatefullServiceChannel(long retryInitialDelayMs, Scheduler scheduler) {
        super(retryInitialDelayMs, scheduler);
    }

    public StateWithChannel getStateWithChannel() {
        return currentStateWithChannel.get();
    }

    @Override
    public void close() {
        if (!shutdown) {
            super.close();
            if (currentStateWithChannel.get() != null && currentStateWithChannel.get().channel != null) {
                currentStateWithChannel.get().channel.close();
            }
        }
    }

    protected abstract StateWithChannel reestablish();

    protected abstract Observable<Void> repopulate(StateWithChannel newState);

    protected abstract void release(StateWithChannel oldState);

    protected void initializeRetryableChannel() {
        currentStateWithChannel.set(reestablish());
        subscribeToChannelLifecycle();
    }

    @Override
    protected void retry() {
        final StateWithChannel newStateWithChannel = reestablish();

        repopulate(newStateWithChannel).subscribe(new Subscriber<Void>() {
            @Override
            public void onCompleted() {
                StateWithChannel oldState = currentStateWithChannel.getAndSet(newStateWithChannel);
                subscribeToChannelLifecycle();
                release(oldState);
                if (oldState.channel != null) {
                    oldState.channel.close();
                }

                logger.info("Channel {} successfully reconnected and the state has been restored", name);
            }

            @Override
            public void onError(Throwable e) {
                logger.error("Failed to reconnect channel " + name, e);
                scheduleRetry();
            }

            @Override
            public void onNext(Void aVoid) {
                // No-op
            }
        });
    }

    private void subscribeToChannelLifecycle() {
        subscribeToChannelLifecycle(getStateWithChannel().getChannel());
    }
}
