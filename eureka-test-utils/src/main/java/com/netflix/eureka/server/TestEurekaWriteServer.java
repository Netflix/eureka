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

import com.google.inject.AbstractModule;
import com.netflix.adminresources.resources.KaryonWebAdminModule;
import com.netflix.eureka.registry.EurekaRegistry;
import com.netflix.eureka.registry.EurekaRegistryImpl;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.registry.SampleApp;
import com.netflix.eureka.server.TestEurekaWriteServer.EurekaServerModule;
import com.netflix.eureka.server.TestEurekaWriteServer.EurekaShutdownModule;
import com.netflix.eureka.server.transport.tcp.discovery.TcpDiscoveryModule;
import com.netflix.eureka.server.transport.tcp.registration.JsonRegistrationModule;
import com.netflix.governator.annotations.Modules;
import com.netflix.karyon.KaryonBootstrap;
import com.netflix.karyon.KaryonServer;
import com.netflix.karyon.ShutdownModule;
import com.netflix.karyon.archaius.ArchaiusBootstrap;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Tomasz Bak
 */
@ArchaiusBootstrap
@KaryonBootstrap(name = "eureka-server")
@Modules(include = {
        EurekaShutdownModule.class,
        KaryonWebAdminModule.class,
        EurekaServerModule.class,
        //HttpRegistrationModule.class,
        JsonRegistrationModule.class,
        TcpDiscoveryModule.class
})
public class TestEurekaWriteServer {

    private static final Set<InstanceInfo> PRESET_INSTANCE_INFOS;
    static {
        PRESET_INSTANCE_INFOS = new HashSet<>();
        PRESET_INSTANCE_INFOS.addAll(SampleApp.Zuul.collectionOf(10));
        PRESET_INSTANCE_INFOS.addAll(SampleApp.Discovery.collectionOf(10));
    }

    public static class EurekaServerModule extends AbstractModule {
        @Override
        protected void configure() {
            EurekaRegistry registry = new EurekaRegistryImpl();
            register(registry, PRESET_INSTANCE_INFOS);
            bind(EurekaRegistry.class).toInstance(registry);
        }
    }

    public static class EurekaShutdownModule extends ShutdownModule {
        public EurekaShutdownModule() {
            super(7777);
        }
    }

    public static void register(EurekaRegistry registry, Set<InstanceInfo> instanceInfos) {
        for (InstanceInfo instanceInfo : instanceInfos) {
            registry.register(instanceInfo);
        }
    }

    public static void main(String[] args) {
        KaryonServer.main(new String[]{TestEurekaWriteServer.class.getName()});
    }
}
