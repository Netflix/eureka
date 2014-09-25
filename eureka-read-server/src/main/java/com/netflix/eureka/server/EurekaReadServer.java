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
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.netflix.adminresources.resources.KaryonWebAdminModule;
import com.netflix.eureka.client.EurekaClient;
import com.netflix.eureka.client.ServerResolver;
import com.netflix.eureka.client.ServerResolver.Protocol;
import com.netflix.eureka.client.ServerResolver.ProtocolType;
import com.netflix.eureka.client.bootstrap.ServerResolvers;
import com.netflix.eureka.server.ReadStartupConfig.ReadCommandLineParser;
import com.netflix.eureka.server.transport.tcp.discovery.TcpDiscoveryModule;
import com.netflix.eureka.transport.EurekaTransports.Codec;
import com.netflix.governator.annotations.Modules;
import com.netflix.governator.guice.LifecycleInjectorBuilder;
import com.netflix.governator.guice.LifecycleInjectorBuilderSuite;
import com.netflix.karyon.KaryonBootstrap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Tomasz Bak
 */
//@ArchaiusBootstrap
@KaryonBootstrap(name = "eureka-read-server")
@Modules(include = KaryonWebAdminModule.class)
public class EurekaReadServer extends AbstractEurekaServer<ReadStartupConfig> {

    private static final Logger logger = LoggerFactory.getLogger(EurekaReadServer.class);

    private final ServerResolver<InetSocketAddress> resolver;

    public EurekaReadServer(ReadStartupConfig config) {
        super(config);
        this.resolver = createResolver();
    }

    @Override
    protected LifecycleInjectorBuilderSuite additionalModules() {
        return new LifecycleInjectorBuilderSuite() {
            @Override
            public void configure(LifecycleInjectorBuilder builder) {
                builder.withModules(
                        new EurekaShutdownModule(config.getShutDownPort()),
                        new TcpDiscoveryModule("eurekaReadServer-transport", config.getDiscoveryPort()),
                        new EurekaReadServerModule(resolver, Codec.Json)
                );
            }
        };
    }

    @Override
    public void start() throws Exception {
        resolver.start();
        super.start();
    }

    private ServerResolver<InetSocketAddress> createResolver() {
        Protocol[] protocols = {
                new Protocol(config.getWriteClusterRegistrationPort(), ProtocolType.TcpRegistration),
                new Protocol(config.getWriteClusterDiscoveryPort(), ProtocolType.TcpDiscovery)
        };

        ServerResolver<InetSocketAddress> resolver = null;
        switch (config.getResolverType()) {
            case "dns":
                resolver = ServerResolvers.forDomainName(config.getRest()[0], protocols);
                break;
            case "inline":
                Set<Protocol> protocolSet = new HashSet<>(Arrays.asList(protocols));
                resolver = ServerResolvers.fromList(protocolSet, config.getRest());
                break;
        }
        return resolver;
    }

    private void selfRegistration() {
        ReadSelfRegistrationExecutor.doSelfRegistration(injector.getInstance(EurekaClient.class), config);
    }

    public static void main(String[] args) {
        System.out.println("Eureka 2.0 Read Server\n");

        ReadCommandLineParser commandLineParser = new ReadCommandLineParser();
        ReadStartupConfig config = null;
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

        logger.info("Starting Eureka Read server with startup configuration: " + config);

        EurekaReadServer server = null;
        try {
            server = new EurekaReadServer(config);
            server.start();
            server.selfRegistration();
        } catch (Exception e) {
            logger.error("Error while starting Eureka Read server.", e);
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
