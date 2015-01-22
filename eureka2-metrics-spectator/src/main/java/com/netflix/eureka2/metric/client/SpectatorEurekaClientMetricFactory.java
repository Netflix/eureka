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

import javax.inject.Inject;
import javax.inject.Named;
import java.util.concurrent.ConcurrentHashMap;

import com.netflix.eureka2.metric.InterestChannelMetrics;
import com.netflix.eureka2.metric.MessageConnectionMetrics;
import com.netflix.eureka2.metric.RegistrationChannelMetrics;
import com.netflix.eureka2.metric.SerializedTaskInvokerMetrics;
import com.netflix.eureka2.metric.SpectatorInterestChannelMetrics;
import com.netflix.eureka2.metric.SpectatorRegistrationChannelMetrics;
import com.netflix.eureka2.metric.SpectatorSerializedTaskInvokerMetrics;
import com.netflix.spectator.api.ExtendedRegistry;

/**
 * @author Tomasz Bak
 */
public class SpectatorEurekaClientMetricFactory extends EurekaClientMetricFactory {

    private final ExtendedRegistry registry;

    private final SpectatorEurekaClientRegistryMetrics registryMetrics;

    private final MessageConnectionMetrics registrationServerConnectionMetrics;

    private final MessageConnectionMetrics discoveryServerConnectionMetrics;

    private final SpectatorRegistrationChannelMetrics registrationChannelMetrics;

    private final SpectatorInterestChannelMetrics interestChannelMetrics;

    private final ConcurrentHashMap<Class<?>, SerializedTaskInvokerMetrics> serializedTaskInvokerMetricsMap = new ConcurrentHashMap<>();

    @Inject
    public SpectatorEurekaClientMetricFactory(
            ExtendedRegistry registry,
            SpectatorEurekaClientRegistryMetrics registryMetrics,
            @Named("registration") MessageConnectionMetrics registrationServerConnectionMetrics,
            @Named("discovery") MessageConnectionMetrics discoveryServerConnectionMetrics,
            SpectatorRegistrationChannelMetrics registrationChannelMetrics,
            SpectatorInterestChannelMetrics interestChannelMetrics) {
        this.registry = registry;
        this.registryMetrics = registryMetrics;
        this.registrationServerConnectionMetrics = registrationServerConnectionMetrics;
        this.discoveryServerConnectionMetrics = discoveryServerConnectionMetrics;
        this.registrationChannelMetrics = registrationChannelMetrics;
        this.interestChannelMetrics = interestChannelMetrics;
    }

    @Override
    public EurekaClientRegistryMetrics getRegistryMetrics() {
        return registryMetrics;
    }

    @Override
    public MessageConnectionMetrics getRegistrationServerConnectionMetrics() {
        return registrationServerConnectionMetrics;
    }

    @Override
    public MessageConnectionMetrics getDiscoveryServerConnectionMetrics() {
        return discoveryServerConnectionMetrics;
    }

    @Override
    public RegistrationChannelMetrics getRegistrationChannelMetrics() {
        return registrationChannelMetrics;
    }

    @Override
    public InterestChannelMetrics getInterestChannelMetrics() {
        return interestChannelMetrics;
    }

    @Override
    public SerializedTaskInvokerMetrics getSerializedTaskInvokerMetrics(Class<?> invokerClass) {
        SerializedTaskInvokerMetrics invokerMetrics = serializedTaskInvokerMetricsMap.get(invokerClass);
        if (invokerMetrics == null) {
            invokerMetrics = serializedTaskInvokerMetricsMap.putIfAbsent(
                    invokerClass,
                    new SpectatorSerializedTaskInvokerMetrics(registry, invokerClass.getSimpleName())
            );
        }
        return invokerMetrics;
    }
}
