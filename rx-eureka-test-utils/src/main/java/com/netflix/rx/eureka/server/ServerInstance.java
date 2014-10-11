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

package com.netflix.rx.eureka.server;

import java.net.InetSocketAddress;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.netflix.rx.eureka.client.EurekaClient;
import com.netflix.rx.eureka.client.EurekaClients;
import com.netflix.rx.eureka.client.ServerResolver;
import com.netflix.rx.eureka.server.spi.ExtensionLoader;
import com.netflix.governator.guice.LifecycleInjector;
import com.netflix.governator.guice.LifecycleInjectorBuilder;
import com.netflix.governator.lifecycle.LifecycleManager;

/**
 * @author Tomasz Bak
 */
public abstract class ServerInstance {

    protected Injector injector;
    protected LifecycleManager lifecycleManager;

    protected void setup(Module[] modules) {
        LifecycleInjectorBuilder builder = LifecycleInjector.builder();
        builder.withAdditionalModules(modules);

        // Extensions
        builder.withAdditionalModules(new ExtensionLoader().asModuleArray());

        injector = builder.build().createInjector();

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
        public EurekaWriteServerInstance(final WriteServerConfig config, final ServerResolver<InetSocketAddress> writeClusterResolver) {
            Module[] modules = {
                    new EurekaWriteServerModule(config),
                    new AbstractModule() {
                        @Override
                        protected void configure() {
                            bind(WriteClusterResolverProvider.class).toInstance(new WriteClusterResolverProvider(writeClusterResolver));
                        }
                    }
            };

            setup(modules);
        }
    }

    public static class EurekaReadServerInstance extends ServerInstance {
        public EurekaReadServerInstance(ReadServerConfig config, final ServerResolver<InetSocketAddress> resolver) {
            final EurekaClient eurekaClient = EurekaClients.forRegistrationAndDiscovery(resolver, resolver, config.getCodec()).toBlocking().first();
            Module[] modules = {
                    new EurekaReadServerModule(config, eurekaClient),
            };

            setup(modules);
        }
    }
}
