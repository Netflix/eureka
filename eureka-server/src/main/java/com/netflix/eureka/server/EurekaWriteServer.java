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
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.registry.LeasedInstanceRegistry;
import com.netflix.eureka.server.EurekaWriteServer.EurekaServerModule;
import com.netflix.eureka.server.EurekaWriteServer.EurekaShutdownModule;
import com.netflix.eureka.server.service.EurekaServerService;
import com.netflix.eureka.server.service.EurekaServiceImpl;
import com.netflix.eureka.server.transport.discovery.asynchronous.AvroDiscoveryModule;
import com.netflix.eureka.server.transport.registration.asynchronous.JsonRegistrationModule;
import com.netflix.eureka.server.transport.registration.http.HttpRegistrationModule;
import com.netflix.governator.annotations.Modules;
import com.netflix.karyon.KaryonBootstrap;
import com.netflix.karyon.KaryonServer;
import com.netflix.karyon.ShutdownModule;
import com.netflix.karyon.archaius.ArchaiusBootstrap;

/**
 * @author Tomasz Bak
 */
@ArchaiusBootstrap
@KaryonBootstrap(name = "eureka-server")
@Modules(include = {
        EurekaShutdownModule.class,
        KaryonWebAdminModule.class,
        EurekaServerModule.class,
        HttpRegistrationModule.class,
        JsonRegistrationModule.class,
        AvroDiscoveryModule.class
})
public class EurekaWriteServer {

    public static class EurekaServerModule extends AbstractModule {
        @Override
        protected void configure() {
            // TODO: We need local server instance info...
            InstanceInfo localInstance = new InstanceInfo.Builder().withId("eurekaServer123").build();
            bind(EurekaRegistry.class).toInstance(new LeasedInstanceRegistry(localInstance));
            bind(EurekaServerService.class).to(EurekaServiceImpl.class).asEagerSingleton();
        }
    }

    public static class EurekaShutdownModule extends ShutdownModule {
        public EurekaShutdownModule() {
            super(7777);
        }
    }

    public static void main(String[] args) {
        KaryonServer.main(new String[]{EurekaWriteServer.class.getName()});
    }
}
