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

import java.util.concurrent.Callable;

import com.netflix.eureka2.registry.eviction.EvictionQueue;
import com.netflix.servo.monitor.BasicGauge;
import com.netflix.servo.monitor.Counter;

/**
 * @author Tomasz Bak
 */
public class EvictionQueueMetrics extends EurekaMetrics {

    private final Counter evictionQueueAddCounter;
    private final Counter evictionQueueRemoveCounter;

    public EvictionQueueMetrics() {
        super("evictionQueue");
        evictionQueueAddCounter = newCounter("addedEvictions");
        evictionQueueRemoveCounter = newCounter("removedEvictions");
    }

    public void incrementEvictionQueueAddCounter() {
        evictionQueueAddCounter.increment();
    }

    public void decrementEvictionQueueCounter() {
        evictionQueueRemoveCounter.increment();
    }

    public void setEvictionQueueSizeMonitor(final EvictionQueue evictionQueue) {
        Callable<Integer> registrySizeCallable = new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                return evictionQueue.size();
            }
        };
        BasicGauge<Integer> gauge = new BasicGauge<>(monitorConfig("evictionQueueSize"), registrySizeCallable);
        register(gauge);
    }
}
