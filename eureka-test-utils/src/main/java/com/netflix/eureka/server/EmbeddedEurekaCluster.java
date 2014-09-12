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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.netflix.eureka.registry.AwsDataCenterInfo;
import com.netflix.eureka.registry.AwsDataCenterInfo.AwsDataCenterInfoBuilder;
import com.netflix.eureka.registry.EurekaRegistry;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.registry.InstanceInfo.Builder;
import com.netflix.eureka.registry.LeasedInstanceRegistry;
import com.netflix.eureka.registry.NetworkAddress;
import com.netflix.eureka.server.transport.tcp.discovery.TcpDiscoveryModule;
import com.netflix.eureka.server.transport.tcp.registration.JsonRegistrationModule;
import com.netflix.governator.guice.LifecycleInjector;
import com.netflix.governator.guice.LifecycleInjectorBuilder;
import com.netflix.governator.lifecycle.LifecycleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Run multi-node Eureka write/read clusters within single JVM.
 *
 * @author Tomasz Bak
 */
public class EmbeddedEurekaCluster {

    private static final Logger logger = LoggerFactory.getLogger(EmbeddedEurekaCluster.class);

    private static final String WRITE_SERVER_NAME_PREFIX = "WriteServer";
    private static final int WRITE_SERVER_PORTS_FROM = 7200;

    private final List<ServerInstance> writeInstances = new ArrayList<>();

    public EmbeddedEurekaCluster(int writeCount, int readCount) {
        for (int i = 0; i < writeCount; i++) {
            ServerInstance instance = new ServerInstance(WRITE_SERVER_NAME_PREFIX + i, WRITE_SERVER_PORTS_FROM + 2 * i);
            writeInstances.add(instance);
        }
    }

    public void waitTillShutdown() {
        final CountDownLatch shutdownFinished = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                try {
                    shutdown();
                    logger.info("Leaving main loop - shutdown finished.");
                } finally {
                    shutdownFinished.countDown();
                }
            }
        });
        while (true) {
            try {
                shutdownFinished.await();
                return;
            } catch (InterruptedException e) {
                // IGNORE
            }
        }
    }

    public void shutdown() {
        for (ServerInstance instance : writeInstances) {
            instance.shutdown();
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("ERROR: required number of write and read servers");
            System.exit(-1);
        }
        int writeCount = Integer.valueOf(args[0]);
        int readCount = Integer.valueOf(args[1]);
        new EmbeddedEurekaCluster(writeCount, readCount).waitTillShutdown();
    }

    public static class EmbeddedEurekaServerModule extends AbstractModule {

        private final InstanceInfo localInstance;

        public EmbeddedEurekaServerModule(InstanceInfo localInstance) {
            this.localInstance = localInstance;
        }

        @Override
        protected void configure() {
            bind(EurekaRegistry.class).toInstance(new LeasedInstanceRegistry(localInstance));
        }
    }

    public static class ServerInstance {
        private final Injector injector;
        private final LifecycleManager lifecycleManager;

        public ServerInstance(String serverName, int port) {
            AwsDataCenterInfo dataCenterInfo = new AwsDataCenterInfoBuilder()
                    .withAddresses(NetworkAddress.publicHostNameIPv4("localhost"))
                    .build();

            InstanceInfo localInstance = new Builder()
                    .withId(serverName)
                    .withApp("Discovery2.0 Write")
                    .withAppGroup("Discovery2.0 Write Cluster")
                    .withInstanceLocation(dataCenterInfo)
                    .build();
            Module[] modules = {
                    new JsonRegistrationModule(serverName + "#registration", port),
                    new TcpDiscoveryModule(serverName + "#discovery", port + 1),
                    new EmbeddedEurekaServerModule(localInstance)
            };


            LifecycleInjectorBuilder builder = LifecycleInjector.builder();
            builder.withAdditionalModules(modules);
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
    }
}
