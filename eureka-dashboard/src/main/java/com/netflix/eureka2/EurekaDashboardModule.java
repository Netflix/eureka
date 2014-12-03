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

package com.netflix.eureka2;

import com.google.inject.AbstractModule;
import com.netflix.eureka2.config.EurekaDashboardConfig;
import com.netflix.eureka2.server.config.EurekaCommonConfig;

/**
 * @author Tomasz Bak
 */
public class EurekaDashboardModule extends AbstractModule {

    private final EurekaDashboardConfig config;

    public EurekaDashboardModule() {
        this(null);
    }

    public EurekaDashboardModule(EurekaDashboardConfig config) {
        this.config = config;
    }

    @Override
    protected void configure() {
        if (config == null) {
            bind(EurekaDashboardConfig.class).asEagerSingleton();
        } else {
            bind(EurekaCommonConfig.class).toInstance(config);
            bind(EurekaDashboardConfig.class).toInstance(config);
        }

        bind(DashboardHttpServer.class).asEagerSingleton();
        bind(WebSocketServer.class).asEagerSingleton();
    }
}
