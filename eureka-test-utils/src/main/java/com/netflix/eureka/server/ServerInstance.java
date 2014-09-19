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

import com.google.inject.Injector;
import com.google.inject.Module;
import com.netflix.eureka.client.ServerResolver;
import com.netflix.eureka.client.bootstrap.StaticServerResolver;
import com.netflix.eureka.server.transport.tcp.discovery.TcpDiscoveryModule;
import com.netflix.eureka.server.transport.tcp.registration.JsonRegistrationModule;
import com.netflix.eureka.transport.EurekaTransports.Codec;
import com.netflix.governator.guice.LifecycleInjector;
import com.netflix.governator.guice.LifecycleInjectorBuilder;
import com.netflix.governator.lifecycle.LifecycleManager;

import java.net.InetSocketAddress;

/**
 * @author Tomasz Bak
 */
public abstract class ServerInstance {

    private LifecycleManager lifecycleManager;

    protected void setup(Module[] modules) {
        LifecycleInjectorBuilder builder = LifecycleInjector.builder();
        builder.withAdditionalModules(modules);
        Injector injector = builder.build().createInjector();

        lifecycleManager = injector.getInstance(LifecycleManager.class);
        try {
            lifecycleManager.start();
        } catch (Exception e) {
            throw new RuntimeException("Container setup failure", e);
        }
    }

    public void shutdown() {
        lifecycleManager.close();
    }

    public static class EurekaWriteServerInstance extends ServerInstance {
        public EurekaWriteServerInstance(String serverName, int port) {
            Module[] modules = {
                    new JsonRegistrationModule(serverName + "#registration", port),
                    new TcpDiscoveryModule(serverName + "#discovery", port + 1),
                    new EurekaWriteServerModule(new StaticServerResolver<InetSocketAddress>(), Codec.Json)
            };

            setup(modules);
        }
    }

    public static class EurekaReadServerInstance extends ServerInstance {
        public EurekaReadServerInstance(String serverName, int port, ServerResolver<InetSocketAddress> resolver) {

            Module[] modules = {
                    new TcpDiscoveryModule(serverName + "#discovery", port),
                    new EurekaReadServerModule(resolver, Codec.Json)
            };

            setup(modules);
        }
    }
}
