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

package com.netflix.eureka.server;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.inject.Module;
import com.netflix.adminresources.resources.KaryonWebAdminModule;
import com.netflix.eureka.client.bootstrap.StaticServerResolver;
import com.netflix.eureka.registry.EurekaRegistry;
import com.netflix.eureka.server.WriteStartupConfig.WriteCommandLineParser;
import com.netflix.eureka.server.spi.ExtensionContext;
import com.netflix.eureka.server.spi.ExtensionContext.ExtensionContextBuilder;
import com.netflix.eureka.server.spi.ExtensionLoader;
import com.netflix.eureka.server.transport.tcp.discovery.TcpDiscoveryModule;
import com.netflix.eureka.server.transport.tcp.registration.JsonRegistrationModule;
import com.netflix.eureka.transport.EurekaTransports.Codec;
import com.netflix.governator.annotations.Modules;
import com.netflix.governator.guice.LifecycleInjectorBuilder;
import com.netflix.governator.guice.LifecycleInjectorBuilderSuite;
import com.netflix.karyon.KaryonBootstrap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Arrays.*;

/**
 * @author Tomasz Bak
 */
//@ArchaiusBootstrap
@KaryonBootstrap(name = "eureka-write-server")
@Modules(include = KaryonWebAdminModule.class)
public class EurekaWriteServer extends AbstractEurekaServer<WriteStartupConfig> {

    private static final Logger logger = LoggerFactory.getLogger(EurekaWriteServer.class);

    public EurekaWriteServer(WriteStartupConfig config) {
        super(config);
    }

    @Override
    protected LifecycleInjectorBuilderSuite additionalModules() {
        return new LifecycleInjectorBuilderSuite() {
            @Override
            public void configure(LifecycleInjectorBuilder builder) {
                List<Module> baseModules = Arrays.<Module>asList(
                        new EurekaShutdownModule(config.getShutDownPort()),
                        new JsonRegistrationModule("eurekaWriteServer-registrationTransport", config.getRegistrationPort()),
                        new TcpDiscoveryModule("eurekaWriteServer-discoveryTransport", config.getDiscoveryPort()),
                        new EurekaWriteServerModule(new StaticServerResolver<InetSocketAddress>(), Codec.Json)
                );
                ExtensionContext extensionContext = new ExtensionContextBuilder()
                        .withEurekaClusterName("eureka-write-server")
                        .withInternalReadServerAddress(new InetSocketAddress("localhost", config.getDiscoveryPort()))
                        .withSystemProperties(true)
                        .build();
                List<Module> extModules = asList(new ExtensionLoader(extensionContext, false).asModuleArray());

                List<Module> all = new ArrayList<>(baseModules);
                all.addAll(extModules);
                builder.withModules(all);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private void selfRegistration() {
        WriteSelfRegistrationExecutor.doSelfRegistration(injector.getInstance(EurekaRegistry.class), config);
    }

    public static void main(String[] args) {
        System.out.println("Eureka 2.0 Write Server\n");

        WriteCommandLineParser commandLineParser = new WriteCommandLineParser();
        WriteStartupConfig config = null;
        try {
            config = commandLineParser.build(args);
        } catch (Exception e) {
            System.err.println("ERROR: invalid configuration parameters; " + e.getMessage());
            System.exit(-1);
        }

        if (config.hasHelp()) {
            commandLineParser.printHelp();
            System.exit(0);
        }

        logger.info("Starting Eureka Write server with startup configuration: " + config);

        EurekaWriteServer server = null;
        try {
            server = new EurekaWriteServer(config);
            server.start();
            server.selfRegistration();
        } catch (Exception e) {
            logger.error("Error while starting Eureka Write server.", e);
            if (server != null) {
                server.shutdown();
            }
            System.exit(-1);
        }
        server.waitTillShutdown();

        // In case we have non-daemon threads running
        System.exit(0);
    }
}
