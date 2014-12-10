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

package com.netflix.eureka2.server.channel;

import com.netflix.eureka2.channel.RetryableStatelessServiceChannel;
import com.netflix.eureka2.protocol.replication.ReplicationHello;
import com.netflix.eureka2.protocol.replication.ReplicationHelloReply;
import com.netflix.eureka2.registry.InstanceInfo;
import rx.Observable;
import rx.Scheduler;
import rx.functions.Func0;
import rx.schedulers.Schedulers;

/**
 * An adapter for replication channel, that reconnects automatically on channel failure. This is an
 * abstract class, as {@link com.netflix.eureka2.channel.RetryableStatelessServiceChannel#getChannelHandler()} must
 * be implemented, to provide appropriate handler. This handler is not passed in the constructor as argument
 * due to possible initialization issue caused by circular dependency during object construction.
 *
 * @author Tomasz Bak
 */
public abstract class RetryableSenderReplicationChannel extends RetryableStatelessServiceChannel<ReplicationChannel>
        implements ReplicationChannel {

    private final Func0<ReplicationChannel> channelFactory;

    protected RetryableSenderReplicationChannel(Func0<ReplicationChannel> channelFactory,
                                                long retryInitialDelayMs) {
        this(channelFactory, retryInitialDelayMs, Schedulers.computation());
    }

    protected RetryableSenderReplicationChannel(Func0<ReplicationChannel> channelFactory,
                                                long retryInitialDelayMs,
                                                Scheduler scheduler) {
        super(retryInitialDelayMs, scheduler);
        this.channelFactory = channelFactory;
        initializeRetryableChannel();
    }

    @Override
    public Observable<ReplicationHelloReply> hello(ReplicationHello hello) {
        return getCurrentChannel().hello(hello);
    }

    @Override
    public Observable<Void> register(InstanceInfo instanceInfo) {
        return getCurrentChannel().register(instanceInfo);
    }

    @Override
    public Observable<Void> update(InstanceInfo newInfo) {
        return getCurrentChannel().update(newInfo);
    }

    @Override
    public Observable<Void> unregister(String instanceId) {
        return getCurrentChannel().unregister(instanceId);
    }

    @Override
    protected ReplicationChannel newChannel() {
        return channelFactory.call();
    }
}
