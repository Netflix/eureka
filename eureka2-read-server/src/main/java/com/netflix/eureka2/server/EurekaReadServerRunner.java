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

package com.netflix.eureka2.server;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.util.Modules;
import com.netflix.archaius.inject.ApplicationLayer;
import com.netflix.eureka2.server.config.EurekaServerConfig;
import com.netflix.eureka2.server.module.CommonEurekaServerModule;
import com.netflix.eureka2.server.spi.ExtAbstractModule;
import com.netflix.eureka2.server.spi.ExtAbstractModule.ServerType;
import com.netflix.governator.DefaultGovernatorConfiguration;
import com.netflix.governator.Governator;
import com.netflix.governator.LifecycleInjector;
import com.netflix.governator.auto.ModuleListProviders;
import netflix.adminresources.resources.KaryonWebAdminModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static com.netflix.eureka2.server.config.ServerConfigurationNames.DEFAULT_CONFIG_PREFIX;

/**
 * @author Tomasz Bak
 */
public class EurekaReadServerRunner extends EurekaServerRunner<EurekaReadServer> {

    private static final Logger logger = LoggerFactory.getLogger(EurekaReadServerRunner.class);
    protected final EurekaServerConfig config;

    public EurekaReadServerRunner(EurekaServerConfig config) {
        super(EurekaReadServer.class);
        this.config = config;
    }

    public EurekaReadServerRunner(String name) {
        super(name, EurekaReadServer.class);
        config = null;
    }

    @Override
    protected List<Module> getModules() {
        Module configModule;

        if (config == null && name == null) {
            configModule = EurekaReadServerConfigurationModule.fromArchaius(DEFAULT_CONFIG_PREFIX);
        } else if (config == null) {  // have name
            configModule = Modules
                    .override(EurekaReadServerConfigurationModule.fromArchaius(DEFAULT_CONFIG_PREFIX))
                    .with(new AbstractModule() {
                        @Override
                        protected void configure() {
                            bind(String.class).annotatedWith(ApplicationLayer.class).toInstance(name);
                        }
                    });
        } else {  // have config
            configModule = EurekaReadServerConfigurationModule.fromConfig(config);
        }

        return asList(
                configModule,
                new CommonEurekaServerModule(),
                new EurekaReadServerModule(),
                new KaryonWebAdminModule()
        );
    }

    @Override
    protected LifecycleInjector createInjector() {
        return Governator.createInjector(
                DefaultGovernatorConfiguration.builder()
                        .addProfile(ServerType.Read.name())
                        .addModuleListProvider(ModuleListProviders.forServiceLoader(ExtAbstractModule.class))
                        .build(),
                getModules()
        );
    }

    public static void main(String[] args) {
        logger.info("Eureka 2.0 Read Server");
        EurekaReadServerRunner runner = new EurekaReadServerRunner("eureka-read-server");
        if (runner.start()) {
            runner.awaitTermination();
        }
        // In case we have non-daemon threads running
        System.exit(0);
    }
}
