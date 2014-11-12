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

package com.netflix.rx.eureka.metric;

import com.netflix.rx.eureka.utils.ServoUtils;
import com.netflix.servo.monitor.LongGauge;

import java.util.EnumMap;

/**
 * Many abstractions in Eureka 2.0 are based on internal FSM, which represents their
 * current state. This class provides common abstraction for exposing FSM states as
 * a collection of metrics.
 *
 * @author Tomasz Bak
 */
public abstract class AbstractStateMachineMetrics<STATE extends Enum<STATE>> extends EurekaMetrics {
    private final EnumMap<STATE, LongGauge> stateCounters;

    protected AbstractStateMachineMetrics(String id, Class<STATE> stateClass) {
        super(id);
        this.stateCounters = new EnumMap<STATE, LongGauge>(stateClass);
        for (STATE s : stateClass.getEnumConstants()) {
            LongGauge counter = new LongGauge(monitorConfig("state." + s.name()));
            stateCounters.put(s, counter);
            register(counter);
        }
    }

    public void incrementStateCounter(STATE state) {
        ServoUtils.incrementLongGauge(stateCounters.get(state));
    }

    public void stateTransition(STATE from, STATE to) {
        decrementStateCounter(from);
        incrementStateCounter(to);
    }

    public void decrementStateCounter(STATE state) {
        ServoUtils.decrementLongGauge(stateCounters.get(state));
    }
}
