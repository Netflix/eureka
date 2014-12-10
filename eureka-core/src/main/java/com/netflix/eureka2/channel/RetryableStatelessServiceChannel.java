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

import rx.Scheduler;

/**
 * Channel adapter that reconnects underlying channel in case of failures. As there is no
 * state in the channel that might have to be restored prior to hanging over the new instance
 * to the client, it invokes the provided {@link ChannelHandler} immediately after a new channel is setup.
 *
 * @author Tomasz Bak
 */
public abstract class RetryableStatelessServiceChannel<C extends ServiceChannel> extends RetryableServiceChannel<C> {

    public interface ChannelHandler<C extends ServiceChannel> {

        void reconnect();

        void close();
    }

    private volatile C currentChannel;

    protected RetryableStatelessServiceChannel(long retryInitialDelayMs, Scheduler scheduler) {
        super(retryInitialDelayMs, scheduler);
    }

    protected void initializeRetryableChannel() {
        retry();
    }

    @Override
    public void close() {
        if (!shutdown) {
            super.close();
            currentChannel.close();
        }
    }

    protected abstract ChannelHandler<C> getChannelHandler();

    protected abstract C newChannel();

    protected C getCurrentChannel() {
        return currentChannel;
    }

    @Override
    protected void retry() {
        currentChannel = newChannel();
        subscribeToChannelLifecycle(currentChannel);
        getChannelHandler().reconnect();
    }
}
