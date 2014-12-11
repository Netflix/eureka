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

import java.net.InetSocketAddress;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.netflix.eureka2.client.Eureka;
import com.netflix.eureka2.client.EurekaClient;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.server.config.BridgeServerConfig;
import com.netflix.eureka2.server.config.EurekaServerConfig;
import com.netflix.eureka2.server.config.WriteServerConfig;
import com.netflix.eureka2.server.spi.ExtensionLoader;
import com.netflix.governator.guice.LifecycleInjector;
import com.netflix.governator.guice.LifecycleInjectorBuilder;
import com.netflix.governator.lifecycle.LifecycleManager;
import rx.Observable;

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

        public EurekaWriteServerInstance(final WriteServerConfig config, final Observable<ChangeNotification<InetSocketAddress>> replicationPeers) {
            Module[] modules = {
                    new EurekaWriteServerModule(config),
                    new AbstractModule() {
                        @Override
                        protected void configure() {
                            bind(ReplicationPeerAddressesProvider.class).toInstance(new ReplicationPeerAddressesProvider(replicationPeers));
                        }
                    }
            };

            setup(modules);
        }
    }

    public static class EurekaReadServerInstance extends ServerInstance {

        public EurekaReadServerInstance(EurekaServerConfig config, final ServerResolver registrationResolver,
                                        ServerResolver discoveryResolver) {
            final EurekaClient eurekaClient = Eureka.newClientBuilder(discoveryResolver, registrationResolver)
                    .withCodec(config.getCodec()).build();
            Module[] modules = {
                    new EurekaReadServerModule(config, eurekaClient),
            };

            setup(modules);
        }
    }

    public static class EurekaBridgeServerInstance extends ServerInstance {

        public EurekaBridgeServerInstance(final BridgeServerConfig config, final Observable<ChangeNotification<InetSocketAddress>> replicationPeers) {
            Module[] modules = {
                    new EurekaBridgeServerModule(config),
                    new AbstractModule() {
                        @Override
                        protected void configure() {
                            bind(ReplicationPeerAddressesProvider.class).toInstance(new ReplicationPeerAddressesProvider(replicationPeers));
                        }
                    }
            };

            setup(modules);
        }
    }
}
