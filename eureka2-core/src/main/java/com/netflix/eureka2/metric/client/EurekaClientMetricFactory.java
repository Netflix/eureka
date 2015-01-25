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

import com.netflix.eureka2.metric.InterestChannelMetrics;
import com.netflix.eureka2.metric.MessageConnectionMetrics;
import com.netflix.eureka2.metric.RegistrationChannelMetrics;
import com.netflix.eureka2.metric.SerializedTaskInvokerMetrics;
import com.netflix.eureka2.metric.noop.NoOpEurekaClientMetricFactory;

/**
 * @author Tomasz Bak
 */
public abstract class EurekaClientMetricFactory {

    private static volatile EurekaClientMetricFactory defaultFactory = new NoOpEurekaClientMetricFactory();

    public abstract MessageConnectionMetrics getRegistrationServerConnectionMetrics();

    public abstract MessageConnectionMetrics getDiscoveryServerConnectionMetrics();

    public abstract RegistrationChannelMetrics getRegistrationChannelMetrics();

    public abstract InterestChannelMetrics getInterestChannelMetrics();

    public abstract SerializedTaskInvokerMetrics getSerializedTaskInvokerMetrics(Class<?> serializedTaskInvokerClass);

    public static EurekaClientMetricFactory clientMetrics() {
        return defaultFactory;
    }

    public static void setDefaultMetricFactory(EurekaClientMetricFactory newFactory) {
        defaultFactory = newFactory;
    }
}
