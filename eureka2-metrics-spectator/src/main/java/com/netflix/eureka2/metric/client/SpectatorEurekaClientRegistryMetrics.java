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

package com.netflix.eureka2.metric.client;

import java.util.concurrent.Callable;

import com.netflix.eureka2.metric.SpectatorEurekaMetrics;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.ExtendedRegistry;
import com.netflix.spectator.api.ValueFunction;

/**
 * @author Tomasz Bak
 */
public class SpectatorEurekaClientRegistryMetrics extends SpectatorEurekaMetrics implements EurekaClientRegistryMetrics {

    private final Counter registrationsTotal;
    private final Counter updatesTotal;
    private final Counter unregistrationsTotal;

    public SpectatorEurekaClientRegistryMetrics(ExtendedRegistry registry, String id) {
        super(registry, id);
        registrationsTotal = newCounter("registrationsTotal");
        updatesTotal = newCounter("updatesTotal");
        unregistrationsTotal = newCounter("unregistrationsTotal");
    }

    @Override
    public void incrementRegistrationCounter() {
        registrationsTotal.increment();
    }

    @Override
    public void incrementUnregistrationCounter() {
        unregistrationsTotal.increment();
    }

    @Override
    public void incrementUpdateCounter() {
        updatesTotal.increment();
    }

    @Override
    public void setRegistrySizeMonitor(final Callable<Integer> registrySizeCallable) {
        newLongGauge("registrySize", new ValueFunction() {
            @Override
            public double apply(Object ref) {
                try {
                    return registrySizeCallable.call();
                } catch (Exception e) {
                    throw new RuntimeException("Unexpected error", e);
                }
            }
        });
    }
}
