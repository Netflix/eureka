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

import java.util.Arrays;
import java.util.List;

import com.netflix.eureka2.server.config.WriteCommandLineParser;
import com.netflix.eureka2.server.config.WriteServerConfig;
import com.netflix.eureka2.server.spi.ExtAbstractModule.ServerType;
import com.netflix.eureka2.server.spi.ExtensionLoader;
import com.netflix.governator.guice.BootstrapBinder;
import com.netflix.governator.guice.BootstrapModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Tomasz Bak
 */
public class EurekaWriteServer extends AbstractEurekaServer<WriteServerConfig> {

    private static final Logger logger = LoggerFactory.getLogger(EurekaWriteServer.class);

    public EurekaWriteServer(String name) {
        super(name);
    }

    public EurekaWriteServer(WriteServerConfig config) {
        super(config);
    }

    @Override
    protected void additionalModules(List<BootstrapModule> bootstrapModules) {
        bootstrapModules.add(new BootstrapModule() {
            @Override
            public void configure(BootstrapBinder binder) {
                binder.include(new EurekaWriteServerModule(config));
            }
        });
        bootstrapModules.add(new ExtensionLoader().asBootstrapModule(ServerType.Write));
    }

    public static void main(String[] args) {
        logger.info("Eureka 2.0 Write Server");

        WriteServerConfig config = null;
        if (args.length == 0) {
            logger.info("No command line parameters provided; enabling archaius property loader for server bootstrapping");
        } else {
            WriteCommandLineParser commandLineParser = new WriteCommandLineParser(args);
            try {
                config = commandLineParser.process();
            } catch (Exception e) {
                System.err.println("ERROR: invalid configuration parameters; " + e.getMessage());
                System.exit(-1);
            }

            if (commandLineParser.hasHelpOption()) {
                commandLineParser.printHelp();
                System.exit(0);
            }

            logger.info("Server bootstrapping from command line parameters {}", Arrays.toString(args));
        }

        EurekaWriteServer server = null;
        try {
            server = config != null ? new EurekaWriteServer(config) : new EurekaWriteServer("eureka-write-server");
            server.start();
        } catch (Exception e) {
            logger.error("Error while starting Eureka Write server.", e);
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
