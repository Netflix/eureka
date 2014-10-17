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

package com.netflix.rx.eureka.registry;

import java.util.concurrent.Callable;

import com.netflix.rx.eureka.metric.EurekaMetrics;
import com.netflix.rx.eureka.registry.EurekaRegistry.Origin;
import com.netflix.servo.monitor.BasicGauge;
import com.netflix.servo.monitor.Counter;
import com.netflix.servo.monitor.MonitorConfig;

/**
 * @author Tomasz Bak
 */
public class EurekaRegistryMetrics extends EurekaMetrics {

    private final RegistryActionCounter registrationsCounter;
    private final RegistryActionCounter updatesCounter;
    private final RegistryActionCounter unregistrationsCounter;

    public EurekaRegistryMetrics(String rootName) {
        super(rootName);
        this.registrationsCounter = new RegistryActionCounter(fullName("action.add"));
        this.updatesCounter = new RegistryActionCounter(fullName("action.update"));
        this.unregistrationsCounter = new RegistryActionCounter(fullName("action.remove"));
        register(registrationsCounter, updatesCounter, unregistrationsCounter);
    }

    public void incrementRegistrationCounter(Origin origin) {
        registrationsCounter.increment(origin);
    }

    public void incrementUnregistrationCounter(Origin origin) {
        unregistrationsCounter.increment(origin);
    }

    public void incrementUpdateCounter() {
        updatesCounter.increment(Origin.LOCAL); // TODO: fix registration implementation so we can track it per origin
    }

    public void setRegistrySizeMonitor(Callable<Integer> registrySizeCallable) {
        String name = fullName("registrySize");
        BasicGauge<Integer> gauge = new BasicGauge<>(MonitorConfig.builder(name).build(), registrySizeCallable);
        register(gauge);
    }

    static class RegistryActionCounter extends EurekaMetrics {
        private final Counter localEntryCounter;
        private final Counter replicatedEntryCounter;
        private final Counter totalEntriesCounter;

        RegistryActionCounter(String rootName) {
            super(rootName);
            this.localEntryCounter = newCounter("registrations.local");
            this.replicatedEntryCounter = newCounter("registrations.replicated");
            this.totalEntriesCounter = newCounter("registrations.total");
        }

        void increment(Origin origin) {
            switch (origin) {
                case LOCAL:
                    localEntryCounter.increment();
                    break;
                case REPLICATED:
                    replicatedEntryCounter.increment();
                    break;
            }
            totalEntriesCounter.increment();
        }
    }
}
