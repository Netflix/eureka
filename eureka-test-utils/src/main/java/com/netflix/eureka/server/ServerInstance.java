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

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.netflix.eureka.client.EurekaClient;
import com.netflix.eureka.client.EurekaClients;
import com.netflix.eureka.client.ServerResolver;
import com.netflix.eureka.client.bootstrap.StaticServerResolver;
import com.netflix.eureka.registry.DataCenterInfo;
import com.netflix.eureka.registry.EurekaRegistry;
import com.netflix.eureka.registry.NetworkAddress;
import com.netflix.eureka.registry.datacenter.BasicDataCenterInfo;
import com.netflix.eureka.registry.datacenter.BasicDataCenterInfo.BasicDataCenterInfoBuilder;
import com.netflix.eureka.server.spi.ExtensionContext;
import com.netflix.eureka.server.spi.ExtensionContext.ExtensionContextBuilder;
import com.netflix.eureka.server.spi.ExtensionLoader;
import com.netflix.eureka.server.transport.tcp.discovery.TcpDiscoveryModule;
import com.netflix.eureka.server.transport.tcp.registration.JsonRegistrationModule;
import com.netflix.eureka.transport.EurekaTransports.Codec;
import com.netflix.governator.guice.LifecycleInjector;
import com.netflix.governator.guice.LifecycleInjectorBuilder;
import com.netflix.governator.lifecycle.LifecycleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Tomasz Bak
 */
public abstract class ServerInstance {

    private static final Logger logger = LoggerFactory.getLogger(ServerInstance.class);

    private static final DataCenterInfo DATA_CENTER_INFO = new BasicDataCenterInfoBuilder<BasicDataCenterInfo>()
            .withName("local-dev")
            .withAddresses(NetworkAddress.publicHostNameIPv4("localhost"))
            .build();


    protected Injector injector;
    protected LifecycleManager lifecycleManager;

    protected void setup(final ExtensionContext extensionContext, Module[] modules) {
        LifecycleInjectorBuilder builder = LifecycleInjector.builder();
        builder.withAdditionalModules(modules);

        // Extension context
        builder.withAdditionalModules(new AbstractModule() {
            @Override
            protected void configure() {
                bind(ExtensionContext.class).toInstance(extensionContext);
            }
        });
        // Extensions
        builder.withAdditionalModules(new ExtensionLoader(extensionContext, true).asModuleArray());

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
        public EurekaWriteServerInstance(WriteStartupConfig config) {
            ExtensionContext context = new ExtensionContextBuilder()
                    .withEurekaClusterName(config.getAppName())
                    .withInternalReadServerAddress(new InetSocketAddress("localhost", config.getDiscoveryPort()))
                    .withSystemProperties(true)
                    .build();
            Module[] modules = {
                    new JsonRegistrationModule(config.getAppName() + "#registration", config.getRegistrationPort()),
                    new TcpDiscoveryModule(config.getAppName() + "#discovery", config.getDiscoveryPort()),
                    new EurekaWriteServerModule(new StaticServerResolver<InetSocketAddress>(), Codec.Json)
            };

            setup(context, modules);
            //noinspection unchecked
            WriteSelfRegistrationExecutor.doSelfRegistration(injector.getInstance(EurekaRegistry.class), DATA_CENTER_INFO, config);
        }
    }

    public static class EurekaReadServerInstance extends ServerInstance {
        public EurekaReadServerInstance(ReadStartupConfig config, ServerResolver<InetSocketAddress> resolver) {
            ExtensionContext context = new ExtensionContextBuilder()
                    .withEurekaClusterName(config.getAppName())
                    .withInternalReadServerAddress(new InetSocketAddress("localhost", config.getDiscoveryPort()))
                    .withSystemProperties(true)
                    .build();
            Module[] modules = {
                    new TcpDiscoveryModule(config.getAppName() + "#discovery", config.getDiscoveryPort()),
                    new EurekaReadServerModule(resolver, Codec.Json)
            };

            setup(context, modules);
            EurekaClient eurekaClient = EurekaClients.forRegistrationAndDiscovery(resolver, resolver, Codec.Json).toBlocking().first();
            ReadSelfRegistrationExecutor.doSelfRegistration(eurekaClient, DATA_CENTER_INFO, config);
        }
    }
}
