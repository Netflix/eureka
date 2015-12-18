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

import com.google.inject.Module;
import com.netflix.eureka2.ext.grpc.model.GrpcModelsInjector;
import com.netflix.eureka2.model.StdModelsInjector;
import com.netflix.eureka2.server.config.EurekaServerConfig;
import com.netflix.eureka2.server.module.CommonEurekaServerModule;
import com.netflix.eureka2.server.spi.ExtAbstractModule;
import com.netflix.eureka2.server.spi.ExtAbstractModule.ServerType;
import com.netflix.governator.LifecycleInjector;
import com.netflix.governator.auto.ModuleListProviders;
import com.netflix.karyon.DefaultKaryonConfiguration;
import com.netflix.karyon.Karyon;
import com.netflix.karyon.archaius.ArchaiusKaryonConfiguration;
import netflix.adminresources.resources.KaryonWebAdminModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * @author Tomasz Bak
 */
public class EurekaReadServerRunner extends EurekaServerRunner<EurekaReadServer> {

    static {
        GrpcModelsInjector.injectGrpcModels();
    }

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
        Module configModule = (config == null)
                ? EurekaReadServerConfigurationModule.fromArchaius()
                : EurekaReadServerConfigurationModule.fromConfig(config);

        return asList(
                configModule,
                new CommonEurekaServerModule(),
                new EurekaReadServerModule(),
                new KaryonWebAdminModule()
        );
    }

    @Override
    protected LifecycleInjector createInjector() {
        DefaultKaryonConfiguration.Builder configurationBuilder = (name == null)
                ? ArchaiusKaryonConfiguration.builder()
                : ArchaiusKaryonConfiguration.builder().withConfigName(name);

        configurationBuilder
                .addProfile(ServerType.Read.name())
                .addModuleListProvider(ModuleListProviders.forServiceLoader(ExtAbstractModule.class));

        return Karyon.createInjector(
                configurationBuilder.build(),
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
