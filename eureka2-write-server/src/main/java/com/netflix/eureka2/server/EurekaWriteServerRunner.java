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
import com.netflix.eureka2.server.config.WriteServerConfig;
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

import java.util.Arrays;
import java.util.List;

import static com.netflix.eureka2.server.config.ServerConfigurationNames.DEFAULT_CONFIG_PREFIX;

/**
 * @author Tomasz Bak
 */
public class EurekaWriteServerRunner extends EurekaServerRunner<EurekaWriteServer> {

    private static final Logger logger = LoggerFactory.getLogger(EurekaWriteServerRunner.class);

    protected final WriteServerConfig config;

    public EurekaWriteServerRunner(WriteServerConfig config) {
        super(EurekaWriteServer.class);
        this.config = config;
    }

    public EurekaWriteServerRunner(String name) {
        super(name, EurekaWriteServer.class);
        config = null;
    }

    @Override
    protected List<Module> getModules() {
        Module configModule;

        if (config == null && name == null) {
            configModule = EurekaWriteServerConfigurationModule.fromArchaius(DEFAULT_CONFIG_PREFIX);
        } else if (config == null) {  // have name
            configModule = Modules
                    .override(EurekaWriteServerConfigurationModule.fromArchaius(DEFAULT_CONFIG_PREFIX))
                    .with(new AbstractModule() {
                        @Override
                        protected void configure() {
                            bind(String.class).annotatedWith(ApplicationLayer.class).toInstance(name);
                        }
                    });
        } else {  // have config
            configModule = EurekaWriteServerConfigurationModule.fromConfig(config);
        }

        return Arrays.asList(
                configModule,
                new CommonEurekaServerModule(),
                new EurekaWriteServerModule(),
                new KaryonWebAdminModule()
        );
    }

    @Override
    protected LifecycleInjector createInjector() {

        return Governator.createInjector(
                DefaultGovernatorConfiguration.builder()
                        .addProfile(ServerType.Write.name())
                        .addModuleListProvider(ModuleListProviders.forServiceLoader(ExtAbstractModule.class))
                        .build(),
                getModules()
        );
    }

    public static void main(String[] args) {
        logger.info("Eureka 2.0 Write Server");
        EurekaWriteServerRunner runner = new EurekaWriteServerRunner("eureka-write-server");
        if (runner.start()) {
            runner.awaitTermination();
        }
        // In case we have non-daemon threads running
        System.exit(0);
    }
}
