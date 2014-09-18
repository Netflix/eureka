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

import com.netflix.adminresources.resources.KaryonWebAdminModule;
import com.netflix.eureka.client.ServerResolver;
import com.netflix.eureka.client.ServerResolver.Protocol;
import com.netflix.eureka.client.bootstrap.ServerResolvers;
import com.netflix.eureka.server.transport.tcp.discovery.TcpDiscoveryModule;
import com.netflix.eureka.transport.EurekaTransports.Codec;
import com.netflix.governator.annotations.Modules;
import com.netflix.governator.guice.LifecycleInjectorBuilder;
import com.netflix.governator.guice.LifecycleInjectorBuilderSuite;
import com.netflix.karyon.KaryonBootstrap;
import com.netflix.karyon.archaius.ArchaiusBootstrap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

import static java.util.Arrays.copyOfRange;

/**
 * TODO: register Eureka Read server with the write cluster
 *
 * @author Tomasz Bak
 */
@ArchaiusBootstrap
@KaryonBootstrap(name = "eureka-read-server")
@Modules(include = KaryonWebAdminModule.class)
public class EurekaReadServer extends AbstractEurekaServer {

    private static final Logger logger = LoggerFactory.getLogger(EurekaReadServer.class);

    private final ServerResolver<InetSocketAddress> resolver;
    private final int shutdownPort;

    public EurekaReadServer(ServerResolver<InetSocketAddress> resolver, int shutdownPort) {
        this.resolver = resolver;
        this.shutdownPort = shutdownPort;
    }

    @Override
    protected LifecycleInjectorBuilderSuite additionalModules() {
        return new LifecycleInjectorBuilderSuite() {
            @Override
            public void configure(LifecycleInjectorBuilder builder) {
                builder.withModules(
                        new EurekaShutdownModule(shutdownPort),
                        new TcpDiscoveryModule("eurekaReadServer-transport", 7004),
                        new EurekaReadServerModule(resolver, Codec.Json)
                );
            }
        };
    }

    public static void usage() {
        System.out.println("Usage:");
        System.out.println("    -t dns <write_cluster_dns>     resolve write cluster from a domain name");
        System.out.println("    -t inline (<host>[:<port>])+  configure write cluster resolver with");
        System.out.println("                                  provided list of host[:port] entries");
        System.out.println();
    }

    private static void cliSyntaxError(String message) {
        System.err.println("ERROR: " + message);
        usage();
        System.exit(-1);
    }

    public static void main(String[] args) {
        if (args.length < 3) {
            cliSyntaxError("insufficient number of parameters");
        }
        if (!"-t".equals(args[0])) {
            cliSyntaxError("expected -t option followed by write server cluster resolution method");
        }
        ServerResolver<InetSocketAddress> resolver = null;
        switch (args[1]) {
            case "dns":
                resolver = ServerResolvers.forDomainName(Protocol.TcpDiscovery, args[2]);
                break;
            case "inline":
                resolver = ServerResolvers.fromList(Protocol.TcpDiscovery, copyOfRange(args, 2, args.length));
                break;
            default:
                cliSyntaxError("unrecognized server resolver type " + args[1]);
                break;
        }

        EurekaReadServer server = null;
        try {
            server = new EurekaReadServer(resolver, 7789);
            server.start();
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
