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

package com.netflix.eureka2.client;

import com.netflix.eureka2.config.BasicEurekaRegistryConfig;
import com.netflix.eureka2.config.BasicEurekaTransportConfig;
import com.netflix.eureka2.config.EurekaRegistryConfig;
import com.netflix.eureka2.config.EurekaTransportConfig;
import com.netflix.eureka2.metric.EurekaRegistryMetricFactory;
import com.netflix.eureka2.metric.client.EurekaClientMetricFactory;

/**
 * The abstract client builder handles common components such as metrics and
 * configs (in the future this may morph into common components such as actual
 * transport modules and/or registry modules).
 *
 * @author David Liu
 */
abstract class AbstractClientBuilder<CLIENT, T extends AbstractClientBuilder<CLIENT, T>> {

    // configs
    protected EurekaTransportConfig transportConfig;
    protected EurekaRegistryConfig registryConfig;

    // metrics
    protected EurekaClientMetricFactory clientMetricFactory;
    protected EurekaRegistryMetricFactory registryMetricFactory;

    public T withTransportConfig(EurekaTransportConfig transportConfig) {
        this.transportConfig = transportConfig;
        return self();
    }

    public T withRegistryConfig(EurekaRegistryConfig registryConfig) {
        this.registryConfig = registryConfig;
        return self();
    }

    public T withClientMetricFactory(EurekaClientMetricFactory clientMetricFactory) {
        this.clientMetricFactory = clientMetricFactory;
        return self();
    }

    public T withRegistryMetricFactory(EurekaRegistryMetricFactory registryMetricFactory) {
        this.registryMetricFactory = registryMetricFactory;
        return self();
    }

    public CLIENT build() {
        if (transportConfig == null) {
            transportConfig = new BasicEurekaTransportConfig.Builder().build();
        }

        if (registryConfig == null) {
            registryConfig = new BasicEurekaRegistryConfig.Builder().build();
        }

        if (clientMetricFactory == null) {
            clientMetricFactory = EurekaClientMetricFactory.clientMetrics();
        }

        if (registryMetricFactory == null) {
            registryMetricFactory = EurekaRegistryMetricFactory.registryMetrics();
        }

        return buildClient();
    }

    protected abstract CLIENT buildClient();

    @SuppressWarnings("unchecked")
    protected T self() {
        return (T) this;
    }
}
