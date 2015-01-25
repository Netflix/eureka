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
import javax.inject.Singleton;
import java.util.concurrent.ConcurrentHashMap;

import com.netflix.eureka2.metric.InterestChannelMetrics;
import com.netflix.eureka2.metric.MessageConnectionMetrics;
import com.netflix.eureka2.metric.RegistrationChannelMetrics;
import com.netflix.eureka2.metric.SerializedTaskInvokerMetrics;
import com.netflix.eureka2.metric.SpectatorInterestChannelMetrics;
import com.netflix.eureka2.metric.SpectatorMessageConnectionMetrics;
import com.netflix.eureka2.metric.SpectatorRegistrationChannelMetrics;
import com.netflix.eureka2.metric.SpectatorSerializedTaskInvokerMetrics;
import com.netflix.spectator.api.ExtendedRegistry;

/**
 * @author Tomasz Bak
 */
@Singleton
public class SpectatorEurekaClientMetricFactory extends EurekaClientMetricFactory {

    private final ExtendedRegistry registry;

    private final MessageConnectionMetrics registrationServerConnectionMetrics;

    private final MessageConnectionMetrics discoveryServerConnectionMetrics;

    private final SpectatorRegistrationChannelMetrics registrationChannelMetrics;

    private final SpectatorInterestChannelMetrics interestChannelMetrics;

    private final ConcurrentHashMap<Class<?>, SerializedTaskInvokerMetrics> serializedTaskInvokerMetricsMap = new ConcurrentHashMap<>();

    @Inject
    public SpectatorEurekaClientMetricFactory(ExtendedRegistry registry) {
        this.registry = registry;
        this.registrationServerConnectionMetrics = new SpectatorMessageConnectionMetrics(registry, "registration");
        this.discoveryServerConnectionMetrics = new SpectatorMessageConnectionMetrics(registry, "discovery");
        this.registrationChannelMetrics = new SpectatorRegistrationChannelMetrics(registry, "client");
        this.interestChannelMetrics = new SpectatorInterestChannelMetrics(registry, "client");
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
            serializedTaskInvokerMetricsMap.putIfAbsent(
                    invokerClass,
                    new SpectatorSerializedTaskInvokerMetrics(registry, invokerClass.getSimpleName())
            );
            return serializedTaskInvokerMetricsMap.get(invokerClass);
        }
        return invokerMetrics;
    }
}
