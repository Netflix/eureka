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
import java.util.List;
import java.util.concurrent.CountDownLatch;

import com.netflix.eureka.client.ServerResolver.Protocol;
import com.netflix.eureka.client.bootstrap.StaticServerResolver;
import com.netflix.eureka.server.ServerInstance.EurekaReadServerInstance;
import com.netflix.eureka.server.ServerInstance.EurekaWriteServerInstance;
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
    private static final String READ_SERVER_NAME_PREFIX = "ReadServer";
    private static final int READ_SERVER_PORTS_FROM = 7300;

    private final List<ServerInstance> writeInstances = new ArrayList<>();
    private final List<ServerInstance> readInstances = new ArrayList<>();

    public EmbeddedEurekaCluster(int writeCount, int readCount) {
        StaticServerResolver<InetSocketAddress> writeClusterResolver = new StaticServerResolver<>();
        for (int i = 0; i < writeCount; i++) {
            int port = WRITE_SERVER_PORTS_FROM + 2 * i;
            ServerInstance instance = new EurekaWriteServerInstance(WRITE_SERVER_NAME_PREFIX + i, port);
            writeInstances.add(instance);
            writeClusterResolver.addServer(new InetSocketAddress("localhost", port + 1), Protocol.TcpDiscovery);
        }
        for (int i = 0; i < readCount; i++) {
            ServerInstance instance = new EurekaReadServerInstance(READ_SERVER_NAME_PREFIX + i, READ_SERVER_PORTS_FROM + i, writeClusterResolver);
            readInstances.add(instance);
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
        for (ServerInstance instance : readInstances) {
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
}
