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
import com.netflix.karyon.archaius.ArchaiusBootstrap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Arrays.*;

/**
 * @author Tomasz Bak
 */
@ArchaiusBootstrap
@KaryonBootstrap(name = "eureka-write-server")
@Modules(include = KaryonWebAdminModule.class)
public class EurekaWriteServer extends AbstractEurekaServer {

    private static final Logger logger = LoggerFactory.getLogger(EurekaWriteServer.class);

    private final int shutdownPort;

    public EurekaWriteServer(int shutdownPort) {
        this.shutdownPort = shutdownPort;
    }

    @Override
    protected LifecycleInjectorBuilderSuite additionalModules() {
        return new LifecycleInjectorBuilderSuite() {
            @Override
            public void configure(LifecycleInjectorBuilder builder) {
                List<Module> baseModules = Arrays.<Module>asList(
                        new EurekaShutdownModule(shutdownPort),
                        new JsonRegistrationModule("eurekaWriteServer-registrationTransport", 7002),
                        new TcpDiscoveryModule("eurekaWriteServer-discoveryTransport", 7003),
                        new EurekaWriteServerModule(new StaticServerResolver<InetSocketAddress>(), Codec.Json)
                );
                ExtensionContext extensionContext = new ExtensionContextBuilder()
                        .withEurekaClusterName("eureka-write-server")
                        .withInternalReadServerAddress(new InetSocketAddress("localhost", 7003))
                        .withSystemProperties(true)
                        .build();
                List<Module> extModules = asList(new ExtensionLoader(extensionContext, false).asModuleArray());

                List<Module> all = new ArrayList<>(baseModules);
                all.addAll(extModules);
                builder.withModules(all);
            }
        };
    }

    public static void main(String[] args) {
        EurekaWriteServer server = null;
        try {
            server = new EurekaWriteServer(7788);
            server.start();
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
