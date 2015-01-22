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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.netflix.spectator.api.ExtendedRegistry;
import com.netflix.spectator.api.ValueFunction;

/**
 * Many abstractions in Eureka 2.0 are based on internal FSM, which represents their
 * current state. This class provides common abstraction for exposing FSM states as
 * a collection of metrics.
 *
 * @author Tomasz Bak
 */
public abstract class AbstractStateMachineMetrics<STATE extends Enum<STATE>> extends SpectatorEurekaMetrics {

    private final Map<STATE, AtomicInteger> stateCounters = new HashMap<>();

    protected AbstractStateMachineMetrics(ExtendedRegistry registry, String id, Class<STATE> stateClass) {
        super(registry, id);
        for (STATE s : stateClass.getEnumConstants()) {
            final AtomicInteger counter = new AtomicInteger();
            stateCounters.put(s, counter);
            newLongGauge("state." + s.name(), new ValueFunction() {
                @Override
                public double apply(Object ref) {
                    return counter.get();
                }
            });
        }
    }

    public void incrementStateCounter(STATE state) {
        stateCounters.get(state).incrementAndGet();
    }

    public void stateTransition(STATE from, STATE to) {
        decrementStateCounter(from);
        incrementStateCounter(to);
    }

    public void decrementStateCounter(STATE state) {
        stateCounters.get(state).decrementAndGet();
    }
}
