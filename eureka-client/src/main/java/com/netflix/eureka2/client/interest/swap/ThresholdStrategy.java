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

package com.netflix.eureka2.client.interest.swap;

import com.netflix.eureka2.registry.EurekaRegistry;
import rx.Scheduler;
import rx.schedulers.Schedulers;

/**
 * @author Tomasz Bak
 */
public class ThresholdStrategy implements RegistrySwapStrategy {

    public static final long DEFAULT_MIN_PERCENTAGE = 80;
    public static final long DEFAULT_RELAX_INTERVAL = 1000;

    private final long minPercentage;
    private final long relaxIntervalMs;
    private final Scheduler scheduler;

    private long startTime = -1;

    /**
     * @param minPercentage minimum required registry level in percentage
     * @param relaxIntervalMs interval at which the minPercentage will be relaxed/decreased by 1
     */
    public ThresholdStrategy(long minPercentage, long relaxIntervalMs, Scheduler scheduler) {
        this.minPercentage = minPercentage;
        this.relaxIntervalMs = relaxIntervalMs;
        this.scheduler = scheduler;
    }

    @Override
    public boolean isReadyToSwap(EurekaRegistry originalRegistry, EurekaRegistry newRegistry) {
        if (originalRegistry.size() == 0) {
            return true;
        }

        if (startTime == -1) {
            startTime = scheduler.now();
        }

        long delay = scheduler.now() - startTime;
        long expectedPercentage = minPercentage - delay / relaxIntervalMs;

        long currentPercentage = newRegistry.size() * 100 / originalRegistry.size();
        return currentPercentage >= expectedPercentage;
    }

    public static RegistrySwapStrategyFactory factoryFor(long minPercentage, long relaxIntervalMs) {
        return factoryFor(minPercentage, relaxIntervalMs, Schedulers.computation());
    }

    public static RegistrySwapStrategyFactory factoryFor(final long minPercentage, final long relaxIntervalMs, final Scheduler scheduler) {
        return new RegistrySwapStrategyFactory() {
            @Override
            public RegistrySwapStrategy newInstance() {
                return new ThresholdStrategy(minPercentage, relaxIntervalMs, scheduler);
            }
        };
    }

    public static RegistrySwapStrategyFactory factoryFor(Scheduler scheduler) {
        return factoryFor(DEFAULT_MIN_PERCENTAGE, DEFAULT_RELAX_INTERVAL, scheduler);
    }
}
