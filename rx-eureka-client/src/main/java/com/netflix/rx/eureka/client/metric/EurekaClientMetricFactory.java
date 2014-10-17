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

import javax.inject.Inject;
import javax.inject.Named;

import com.netflix.rx.eureka.client.transport.EurekaClientConnectionMetrics;
import com.netflix.rx.eureka.registry.EurekaRegistryMetrics;

/**
 * @author Tomasz Bak
 */
public class EurekaClientMetricFactory {

    private static EurekaClientMetricFactory INSTANCE;

    private final EurekaRegistryMetrics registryMetrics;

    private final EurekaClientConnectionMetrics registrationServerConnectionMetrics;

    private final EurekaClientConnectionMetrics discoveryServerConnectionMetrics;

    @Inject
    public EurekaClientMetricFactory(EurekaRegistryMetrics registryMetrics,
                                     @Named("registration") EurekaClientConnectionMetrics registrationServerConnectionMetrics,
                                     @Named("discovery") EurekaClientConnectionMetrics discoveryServerConnectionMetrics) {
        this.registryMetrics = registryMetrics;
        this.registrationServerConnectionMetrics = registrationServerConnectionMetrics;
        this.discoveryServerConnectionMetrics = discoveryServerConnectionMetrics;
    }

    public EurekaRegistryMetrics getRegistryMetrics() {
        return registryMetrics;
    }

    public EurekaClientConnectionMetrics getRegistrationServerConnectionMetrics() {
        return registrationServerConnectionMetrics;
    }

    public EurekaClientConnectionMetrics getDiscoveryServerConnectionMetrics() {
        return discoveryServerConnectionMetrics;
    }

    public static EurekaClientMetricFactory clientMetrics() {
        if (INSTANCE == null) {
            synchronized (EurekaClientMetricFactory.class) {
                EurekaRegistryMetrics registryMetrics = new EurekaRegistryMetrics("client");
                registryMetrics.bindMetrics();

                EurekaClientConnectionMetrics registrationServerConnectionMetrics = new EurekaClientConnectionMetrics("registration");
                registrationServerConnectionMetrics.bindMetrics();

                EurekaClientConnectionMetrics discoveryServerConnectionMetrics = new EurekaClientConnectionMetrics("discovery");
                discoveryServerConnectionMetrics.bindMetrics();

                INSTANCE = new EurekaClientMetricFactory(registryMetrics, registrationServerConnectionMetrics, discoveryServerConnectionMetrics);
            }
        }
        return INSTANCE;
    }
}
