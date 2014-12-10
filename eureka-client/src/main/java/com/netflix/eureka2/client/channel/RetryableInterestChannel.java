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

package com.netflix.eureka2.client.channel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.netflix.eureka2.channel.RetryableStatefullServiceChannel;
import com.netflix.eureka2.client.channel.RetryableInterestChannel.RegistryTracker;
import com.netflix.eureka2.client.metric.EurekaClientMetricFactory;
import com.netflix.eureka2.client.registry.EurekaClientRegistry;
import com.netflix.eureka2.client.registry.EurekaClientRegistryImpl;
import com.netflix.eureka2.client.registry.swap.RegistrySwapOperator;
import com.netflix.eureka2.client.registry.swap.RegistrySwapStrategyFactory;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.MultipleInterests;
import com.netflix.eureka2.registry.InstanceInfo;
import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Scheduler;
import rx.Subscriber;
import rx.functions.Action0;
import rx.observers.Subscribers;

/**
 * {@link RetryableInterestChannel} creates a new channel connection in case of failure and
 * re-populates a new registry instance according to the latest interest set.
 *
 * <h1>Assumptions</h1>
 * Client requests are serialized, prior to forwarding to this class.
 *
 * <h1>Failover mode</h1>
 * When there is a channel failure, a new channel/registry is automatically created, and all the current
 * interest subscriptions are automatically subscribed on it. All clients keep using the original registry
 * until, the new registry is restored to the state approved by the associated
 * {@link com.netflix.eureka2.client.registry.swap.RegistrySwapStrategy}.
 *
 * @author Tomasz Bak
 */
public class RetryableInterestChannel extends RetryableStatefullServiceChannel<ClientInterestChannel, RegistryTracker> implements ClientInterestChannel {

    public static final long DEFAULT_INITIAL_DELAY = 80;

    private static final Throwable CHANNEL_FAILURE = new Exception(
            "There was a communication failure, and connection has been reestablished; a new subscription is required");

    private final ClientChannelFactory channelFactory;
    private final RegistrySwapStrategyFactory swapStrategyFactory;
    private final EurekaClientMetricFactory metricFactory;

    public RetryableInterestChannel(
            ClientChannelFactory channelFactory,
            RegistrySwapStrategyFactory swapStrategyFactory,
            EurekaClientMetricFactory metricFactory,
            long retryInitialDelayMs,
            Scheduler scheduler) {
        super(retryInitialDelayMs, scheduler);
        this.channelFactory = channelFactory;
        this.metricFactory = metricFactory;
        this.swapStrategyFactory = swapStrategyFactory;
        initializeRetryableChannel();
    }

    @Override
    public EurekaClientRegistry<InstanceInfo> associatedRegistry() {
        return getStateWithChannel().getState().registry;
    }

    @Override
    public Observable<Void> change(Interest<InstanceInfo> interest) {
        StateWithChannel current = getStateWithChannel();
        ClientInterestChannel currentChannel = current.getChannel();
        RegistryTracker currentState = current.getState();

        return currentChannel.change(interest).doOnCompleted(currentState.createResetAction(interest));
    }

    @Override
    public Observable<Void> appendInterest(Interest<InstanceInfo> toAppend) {
        StateWithChannel current = getStateWithChannel();
        ClientInterestChannel currentChannel = current.getChannel();
        RegistryTracker currentState = current.getState();

        return currentChannel.appendInterest(toAppend)
                .doOnCompleted(currentState.createAppendInterestAction(toAppend));
    }

    @Override
    public Observable<Void> removeInterest(Interest<InstanceInfo> toRemove) {
        StateWithChannel current = getStateWithChannel();
        ClientInterestChannel currentChannel = current.getChannel();
        RegistryTracker currentState = current.getState();

        return currentChannel.removeInterest(toRemove)
                .doOnCompleted(currentState.createRemoveInterestAction(toRemove));
    }

    @Override
    public void close() {
        if (!shutdown) {
            super.close();
            channelFactory.shutdown();
        }
    }

    @Override
    public Observable<Void> asLifecycleObservable() {
        return Observable.empty();
    }

    @Override
    protected StateWithChannel reestablish() {
        final RegistryTracker newState = new RegistryTracker();
        final ClientInterestChannel newChannel = channelFactory.newInterestChannel(newState.registry);
        return new StateWithChannel(newChannel, newState);
    }

    @Override
    protected Observable<Void> repopulate(StateWithChannel newStateWithChannel) {
        final RegistryTracker previousState = getStateWithChannel().getState();
        final RegistryTracker newState = newStateWithChannel.getState();
        final ClientInterestChannel newChannel = newStateWithChannel.getChannel();

        return Observable.create(new OnSubscribe<Void>() {
            @Override
            public void call(final Subscriber<? super Void> subscriber) {
                // Resubscribe
                Interest<InstanceInfo> activeInterests = new MultipleInterests<InstanceInfo>(previousState.interests.keySet());
                newChannel.appendInterest(activeInterests).subscribe();

                // Wait until registry fills up to the expected level.
                newState.registry.forInterest(activeInterests).lift(
                        new RegistrySwapOperator(previousState.registry, newState.registry, swapStrategyFactory)
                ).subscribe(Subscribers.from(subscriber));
            }
        });
    }

    @Override
    protected void release(StateWithChannel oldState) {
        oldState.getState().registry.shutdown(CHANNEL_FAILURE);
    }

    /**
     * {@link RegistryTracker} keeps a reference counted collection of interests, so in case of channel failure
     * we can silently reconnect and re-subscribe.
     */
    class RegistryTracker {

        final EurekaClientRegistry<InstanceInfo> registry = new EurekaClientRegistryImpl(metricFactory.getRegistryMetrics());

        // We use concurrent map here as this map is modified when request acknowledgement completes by a
        // different thread that the one doing the reconnect. Updates are however serialized, as come from the
        // channel event loop.
        final Map<Interest<InstanceInfo>, Integer> interests = new ConcurrentHashMap<>();

        public Action0 createResetAction(final Interest<InstanceInfo> interest) {
            return new Action0() {
                @Override
                public void call() {
                    interests.put(interest, 1);
                }
            };
        }

        Action0 createAppendInterestAction(final Interest<InstanceInfo> interest) {
            return new Action0() {
                @Override
                public void call() {
                    if (interests.containsKey(interest)) {
                        interests.put(interest, interests.get(interest) + 1);
                    } else {
                        interests.put(interest, 1);
                    }
                }
            };
        }

        Action0 createRemoveInterestAction(final Interest<InstanceInfo> interest) {
            return new Action0() {
                @Override
                public void call() {
                    if (interests.containsKey(interest)) {
                        int newLevel = interests.get(interest) - 1;
                        if (newLevel == 0) {
                            interests.remove(interest);
                        } else {
                            interests.put(interest, newLevel);
                        }
                    }
                }
            };
        }
    }
}
