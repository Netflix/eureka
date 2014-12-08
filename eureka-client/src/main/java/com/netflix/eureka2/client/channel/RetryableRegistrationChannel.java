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

import com.netflix.eureka2.channel.RetryableServiceChannel;
import com.netflix.eureka2.registry.InstanceInfo;
import com.netflix.eureka2.channel.RegistrationChannel;
import rx.Observable;
import rx.Scheduler;
import rx.functions.Func0;

/**
 * {@link RetryableRegistrationChannel} creates a new channel connection in case of failure and
 * re-registers a client.
 * <h1>Assumptions</h1>
 * Client requests are serialized, prior to forwarding to this class.
 * <h1>Failover mode</h1>
 * When there is a channel failure, a new channel is automatically recreated, and a client is
 * registered with the latest observed instanceInfo.
 * If there are concurrent registration updates during the reconnect, they will be connected
 * to the original (broken) channel, until the new channel registration succeeds. The former requests
 * will complete with error. It is up to the upper layer to deal with these failures.
 *
 * @author Tomasz Bak
 */
public class RetryableRegistrationChannel
        extends RetryableServiceChannel<RegistrationChannel, Void>
        implements RegistrationChannel {

    private final Func0<RegistrationChannel> channelFactory;
    private InstanceInfo instanceInfo;

    public RetryableRegistrationChannel(Func0<RegistrationChannel> channelFactory, long retryInitialDelayMs, Scheduler scheduler) {
        super(retryInitialDelayMs, scheduler);
        this.channelFactory = channelFactory;
        initializeRetryableConsumer();
    }

    @Override
    public Observable<Void> register(final InstanceInfo newInfo) {
        this.instanceInfo = newInfo;
        return getStateWithChannel().getChannel().register(instanceInfo);
    }

    @Override
    public Observable<Void> update(final InstanceInfo newInfo) {
        this.instanceInfo = newInfo;
        return getStateWithChannel().getChannel().update(newInfo);
    }

    @Override
    public Observable<Void> unregister() {
        instanceInfo = null;
        return getStateWithChannel().getChannel().unregister();
    }

    @Override
    public void close() {
        shutdownRetryableConsumer();
    }

    @Override
    public Observable<Void> asLifecycleObservable() {
        // TODO: We mask failures, but we might want let them resurface during prolonged reconnect issue
        return Observable.empty();
    }

    /*
     * RetryableChannelConsumer template methods.
     */

    @Override
    protected StateWithChannel reestablish() {
        RegistrationChannel newChannel = channelFactory.call();
        return new StateWithChannel(newChannel, null);
    }

    @Override
    protected Observable<Void> repopulate(StateWithChannel newState) {
        if (instanceInfo != null) {
            return newState.getChannel().register(instanceInfo);
        }
        return Observable.empty();
    }

    @Override
    protected void release(StateWithChannel oldState) {
        // No-op
    }
}
