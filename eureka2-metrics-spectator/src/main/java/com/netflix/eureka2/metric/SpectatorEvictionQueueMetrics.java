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

package com.netflix.eureka2.metric;

import java.util.concurrent.atomic.AtomicInteger;

import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.ExtendedRegistry;

/**
 * @author Tomasz Bak
 */
public class SpectatorEvictionQueueMetrics extends SpectatorEurekaMetrics implements EvictionQueueMetrics {

    private final Counter evictionQueueAddCounter;
    private final Counter evictionQueueRemoveCounter;
    private final AtomicInteger evictionQueueSize = new AtomicInteger();

    public SpectatorEvictionQueueMetrics(ExtendedRegistry registry) {
        super(registry, "evictionQueue");
        evictionQueueAddCounter = newCounter("addedEvictions");
        evictionQueueRemoveCounter = newCounter("removedEvictions");
        newGauge("evictionQueueSize", evictionQueueSize);
    }

    @Override
    public void incrementEvictionQueueAddCounter() {
        evictionQueueAddCounter.increment();
    }

    @Override
    public void decrementEvictionQueueCounter() {
        evictionQueueRemoveCounter.increment();
    }

    @Override
    public void setEvictionQueueSize(int evictionQueueSize) {
        this.evictionQueueSize.set(evictionQueueSize);
    }
}
