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

package com.netflix.eureka2.server.spi;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ServiceLoader;

import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.registry.EurekaRegistryView;
import com.netflix.eureka2.server.config.EurekaServerConfig;

/**
 * Eureka extensions discovery is based on {@link ServiceLoader} mechanism.
 *
 * @author Tomasz Bak
 */
@Singleton
public class ExtensionContext {

    private final EurekaServerConfig config;
    private final EurekaRegistryView<InstanceInfo> localRegistryView;

    @Inject
    protected ExtensionContext(EurekaServerConfig config, EurekaRegistryView localRegistryView) {
        this.config = config;
        this.localRegistryView = localRegistryView;
    }

    /**
     * Unique name assigned to read or write cluster.
     */
    public String getEurekaClusterName() {
        return config.getEurekaInstance().getEurekaApplicationName();
    }

    public EurekaRegistryView<InstanceInfo> getLocalRegistryView() {
        return localRegistryView;
    }
}
