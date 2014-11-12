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

package com.netflix.rx.eureka.client.metric;

import com.netflix.rx.eureka.metric.EurekaMetrics;
import com.netflix.servo.monitor.BasicGauge;
import com.netflix.servo.monitor.Counter;

import java.util.concurrent.Callable;

/**
 * @author Tomasz Bak
 */
public class EurekaClientRegistryMetrics extends EurekaMetrics {

    private final Counter registrationsTotal;
    private final Counter updatesTotal;
    private final Counter unregistrationsTotal;

    public EurekaClientRegistryMetrics(String id) {
        super(id);
        registrationsTotal = newCounter("registrationsTotal");
        updatesTotal = newCounter("updatesTotal");
        unregistrationsTotal = newCounter("unregistrationsTotal");
    }

    public void incrementRegistrationCounter() {
        registrationsTotal.increment();
    }

    public void incrementUnregistrationCounter() {
        unregistrationsTotal.increment();
    }

    public void incrementUpdateCounter() {
        updatesTotal.increment();
    }

    public void setRegistrySizeMonitor(Callable<Integer> registrySizeCallable) {
        BasicGauge<Integer> gauge = new BasicGauge<>(monitorConfig("registrySize"), registrySizeCallable);
        register(gauge);
    }
}
