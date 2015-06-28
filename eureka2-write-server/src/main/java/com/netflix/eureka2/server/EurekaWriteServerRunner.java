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
import com.google.inject.util.Modules;
import com.netflix.eureka2.server.config.EurekaCommandLineParser;
import com.netflix.eureka2.server.config.WriteCommandLineParser;
import com.netflix.eureka2.server.config.WriteServerConfig;
import com.netflix.eureka2.server.module.CommonEurekaServerModule;
import com.netflix.eureka2.server.module.EurekaExtensionModule;
import com.netflix.eureka2.server.service.overrides.OverridesModule;
import com.netflix.eureka2.server.spi.ExtAbstractModule.ServerType;
import com.netflix.governator.Governator;
import com.netflix.governator.LifecycleInjector;
import netflix.adminresources.resources.KaryonWebAdminModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Tomasz Bak
 */
public class EurekaWriteServerRunner extends EurekaServerRunner<WriteServerConfig, EurekaWriteServer> {

    private static final Logger logger = LoggerFactory.getLogger(EurekaWriteServerRunner.class);

    public EurekaWriteServerRunner(String[] args) {
        super(args, EurekaWriteServer.class);
    }

    public EurekaWriteServerRunner(WriteServerConfig config) {
        super(config, EurekaWriteServer.class);
    }

    public EurekaWriteServerRunner(String name) {
        super(name, EurekaWriteServer.class);
    }

    @Override
    protected LifecycleInjector createInjector() {
        Module configModule = config == null ? EurekaWriteServerConfigurationModules.fromArchaius() :
                EurekaWriteServerConfigurationModules.fromConfig(config);

        Module applicationModule = Modules.combine(
                configModule,
                new CommonEurekaServerModule(name),
                new OverridesModule(),
                new EurekaExtensionModule(ServerType.Write),
                new EurekaWriteServerModule(),
                new KaryonWebAdminModule()
        );

        return Governator.createInjector(applicationModule);
    }

    @Override
    protected EurekaCommandLineParser newCommandLineParser(String[] args) {
        return new WriteCommandLineParser(args);
    }

    public static void main(String[] args) {
        logger.info("Eureka 2.0 Write Server");
        EurekaWriteServerRunner runner = new EurekaWriteServerRunner(args);
        if (runner.start()) {
            runner.awaitTermination();
        }
        // In case we have non-daemon threads running
        System.exit(0);
    }
}
