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

import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.ExtendedRegistry;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Timer;
import com.netflix.spectator.api.ValueFunction;

/**
 * Base class for component specific metric implementations.
 *
 * @author Tomasz Bak
 */
public abstract class SpectatorEurekaMetrics {

    protected final ExtendedRegistry registry;
    private final String id;

    protected SpectatorEurekaMetrics(ExtendedRegistry registry, String id) {
        this.registry = registry;
        this.id = id;
    }

    protected Id newId(String name) {
        return registry.createId(name)
                .withTag("id", id)
                .withTag("class", getClass().getSimpleName());
    }

    protected Counter newCounter(String name) {
        return registry.counter(newId(name));
    }

    protected <N extends Number> void newGauge(String name, N number) {
        registry.gauge(newId(name), number);
    }

    protected void newLongGauge(String name, ValueFunction valueFunction) {
        registry.gauge(newId(name), 0L, valueFunction);
    }

    protected Timer newTimer(String name) {
        return registry.timer(newId(name));
    }

}
