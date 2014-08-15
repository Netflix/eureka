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

package com.netflix.eureka.client.service;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.netflix.eureka.client.transport.TransportClientProvider;
import com.netflix.eureka.service.ServiceChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Observer;
import rx.functions.Action0;
import rx.functions.Action1;

/**
 * TODO: shutdown policy
 *
 * This class is an attempt to handle channel failure and reconnection scenario.
 *
 * @author Tomasz Bak
 */
public abstract class ServiceChannelMonitor<T, C extends ServiceChannel> {

    private static final Logger logger = LoggerFactory.getLogger(ServiceChannelMonitor.class);

    // TODO: what is the retry policy?
    private static final long RETRY_DELAY = 1;

    private final TransportClientProvider<T> transportClientProvider;

    private final AtomicReference<C> activeChannelRef = new AtomicReference<C>();

    protected ServiceChannelMonitor(TransportClientProvider<T> transportClientProvider) {
        this.transportClientProvider = transportClientProvider;
        activeChannelRef.set(createUnavailableChannel());
    }

    public void start() {
        setupChannel();
    }

    public C activeChannel() {
        return activeChannelRef.get();
    }

    protected abstract C createChannel(T client);

    protected abstract C createUnavailableChannel();

    private void setupChannel() {
        logger.info("Establishing new registration connection...");
        transportClientProvider.connect().subscribe(new Observer<T>() {
            @Override
            public void onCompleted() {
            }

            @Override
            public void onError(Throwable e) {
                logger.error("Could not connect to a registration server - retrying in ");
                Observable.timer(RETRY_DELAY, TimeUnit.SECONDS).forEach(new Action1<Long>() {
                    @Override
                    public void call(Long aLong) {
                        setupChannel();
                    }
                });
            }

            @Override
            public void onNext(T client) {
                C channel = createChannel(client);
                channel.asLifecycleObservable().doOnTerminate(new Action0() {
                    @Override
                    public void call() {
                        activeChannelRef.set(createUnavailableChannel());
                        setupChannel();
                    }
                });
                activeChannelRef.set(channel);
            }
        });
    }
}
