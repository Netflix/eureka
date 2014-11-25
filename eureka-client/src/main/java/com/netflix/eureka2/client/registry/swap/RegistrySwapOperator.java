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

package com.netflix.eureka2.client.registry.swap;

import com.netflix.eureka2.client.registry.EurekaClientRegistry;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.registry.InstanceInfo;
import rx.Observable.Operator;
import rx.Subscriber;
import rx.observers.Subscribers;

/**
 * @author Tomasz Bak
 */
public class RegistrySwapOperator implements Operator<Void, ChangeNotification<InstanceInfo>> {

    private static final IllegalStateException UNEXPECTED_END_OF_STREAM = new IllegalStateException("Unexpected end of interest subscription stream");

    private final EurekaClientRegistry<InstanceInfo> originalRegisry;
    private final EurekaClientRegistry<InstanceInfo> newRegistry;
    private final RegistrySwapStrategyFactory strategyFactory;

    public RegistrySwapOperator(EurekaClientRegistry<InstanceInfo> originalRegisry,
                                EurekaClientRegistry<InstanceInfo> newRegistry,
                                RegistrySwapStrategyFactory strategyFactory) {
        this.originalRegisry = originalRegisry;
        this.newRegistry = newRegistry;
        this.strategyFactory = strategyFactory;
    }

    @Override
    public Subscriber<? super ChangeNotification<InstanceInfo>> call(final Subscriber<? super Void> subscriber) {
        final RegistrySwapStrategy swapStrategy = strategyFactory.newInstance();

        if (swapStrategy.isReadyToSwap(originalRegisry, newRegistry)) {
            subscriber.onCompleted();
            return Subscribers.empty();
        }

        return new Subscriber<ChangeNotification<InstanceInfo>>() {
            @Override
            public void onCompleted() {
                // Interest subscription never completes under normal circumstances.
                subscriber.onError(UNEXPECTED_END_OF_STREAM);
            }

            @Override
            public void onError(Throwable e) {
                subscriber.onError(e);
            }

            @Override
            public void onNext(ChangeNotification<InstanceInfo> instanceInfoChangeNotification) {
                if (swapStrategy.isReadyToSwap(originalRegisry, newRegistry)) {
                    subscriber.onCompleted();
                }
            }
        };
    }
}
