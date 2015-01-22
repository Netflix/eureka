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

import java.util.concurrent.atomic.AtomicReference;

import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.ExtendedRegistry;
import com.netflix.spectator.api.Timer;
import com.netflix.spectator.api.ValueFunction;

/**
 * Base class for component specific metric implementations.
 *
 * @author Tomasz Bak
 */
public abstract class SpectatorEurekaMetrics {

    enum STATE {Init, Up, Down}

    protected final ExtendedRegistry registry;
    private final String id;

    private final AtomicReference<STATE> state = new AtomicReference<>(STATE.Init);

    protected SpectatorEurekaMetrics(ExtendedRegistry registry, String id) {
        this.registry = registry;
        this.id = id;
    }

    protected Counter newCounter(String baseName) {
        return registry.counter(registry.createId(baseName));
    }

    protected void newLongGauge(String baseName, ValueFunction valueFunction) {
        registry.gauge(registry.createId(baseName), 0L, valueFunction);
    }

    protected Timer newTimer(String baseName) {
        return registry.timer(registry.createId(baseName));
    }
}
