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

import com.netflix.eureka2.registry.Source.Origin;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.ExtendedRegistry;
import com.netflix.spectator.api.ValueFunction;


/**
 * @author Tomasz Bak
 */
public class SpectatorEurekaRegistryMetrics extends SpectatorEurekaMetrics implements EurekaRegistryMetrics {

    private final Counter registrationsLocal;
    private final Counter registrationsReplicated;
    private final Counter registrationsTotal;

    private final Counter updatesLocal;
    private final Counter updatesReplicated;
    private final Counter updatesTotal;

    private final Counter unregistrationsLocal;
    private final Counter unregistrationsReplicated;
    private final Counter unregistrationsTotal;

    public SpectatorEurekaRegistryMetrics(ExtendedRegistry registry) {
        super(registry, "eurekaServerRegistry");
        registrationsLocal = newCounter("registrationsLocal");
        registrationsReplicated = newCounter("registrationsReplicated");
        registrationsTotal = newCounter("registrationsTotal");

        updatesLocal = newCounter("updatesLocal");
        updatesReplicated = newCounter("updatesReplicated");
        updatesTotal = newCounter("updatesTotal");

        unregistrationsLocal = newCounter("unregistrationsLocal");
        unregistrationsReplicated = newCounter("unregistrationsReplicated");
        unregistrationsTotal = newCounter("unregistrationsTotal");
    }

    @Override
    public void incrementRegistrationCounter(Origin origin) {
        switch (origin) {
            case LOCAL:
                registrationsLocal.increment();
                break;
            case REPLICATED:
                registrationsReplicated.increment();
                break;
        }
        registrationsTotal.increment();
    }

    @Override
    public void incrementUnregistrationCounter(Origin origin) {
        switch (origin) {
            case LOCAL:
                unregistrationsLocal.increment();
                break;
            case REPLICATED:
                unregistrationsReplicated.increment();
                break;
        }
        unregistrationsTotal.increment();
    }

    @Override
    public void incrementUpdateCounter(Origin origin) {
        switch (origin) {
            case LOCAL:
                updatesLocal.increment();
                break;
            case REPLICATED:
                updatesReplicated.increment();
                break;
        }
        updatesTotal.increment();
    }

    @Override
    public void setRegistrySizeMonitor(final Callable<Integer> registrySizeFun) {
        newLongGauge("registrySize", new ValueFunction() {
            @Override
            public double apply(Object ref) {
                try {
                    return registrySizeFun.call();
                } catch (Exception e) {
                    throw new RuntimeException("Unexpected error", e);
                }
            }
        });
    }

    @Override
    public void setSelfPreservationMonitor(final Callable<Integer> selfPreservationFun) {
        newLongGauge("selfPreservation", new ValueFunction() {
            @Override
            public double apply(Object ref) {
                try {
                    return selfPreservationFun.call();
                } catch (Exception e) {
                    throw new RuntimeException("Unexpected error", e);
                }
            }
        });
    }
}
