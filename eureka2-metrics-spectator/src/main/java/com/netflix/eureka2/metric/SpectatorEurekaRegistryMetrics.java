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

import com.netflix.eureka2.model.Source.Origin;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.ExtendedRegistry;


/**
 * @author Tomasz Bak
 */
public class SpectatorEurekaRegistryMetrics extends SpectatorEurekaMetrics implements EurekaRegistryMetrics {

    private final AtomicInteger registrySize = new AtomicInteger();
    private final AtomicInteger selfPreservation = new AtomicInteger();

    private final Counter registrationsLocal;
    private final Counter registrationsReplicated;
    private final Counter registrationsTotal;

    private final Counter unregistrationsLocal;
    private final Counter unregistrationsReplicated;
    private final Counter unregistrationsTotal;

    public SpectatorEurekaRegistryMetrics(ExtendedRegistry registry) {
        super(registry, "eurekaServerRegistry");

        newGauge("registrySize", registrySize);
        newGauge("selfPreservation", selfPreservation);

        registrationsLocal = newCounter("registrationsLocal");
        registrationsReplicated = newCounter("registrationsReplicated");
        registrationsTotal = newCounter("registrationsTotal");

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
    public void setRegistrySize(int registrySize) {
        this.registrySize.set(registrySize);
    }

    @Override
    public void setSelfPreservation(boolean status) {
        selfPreservation.set(status ? 1 : 0);
    }
}
