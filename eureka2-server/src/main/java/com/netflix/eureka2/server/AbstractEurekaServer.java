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

import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.util.Modules;
import com.netflix.archaius.inject.ApplicationLayer;
import com.netflix.config.ConfigurationManager;
import com.netflix.eureka2.server.config.EurekaCommonConfig;
import com.netflix.eureka2.server.http.EurekaHttpServer;
import com.netflix.eureka2.server.service.EurekaShutdownService;
import com.netflix.governator.DefaultLifecycleListener;
import com.netflix.governator.Governator;
import com.netflix.governator.LifecycleInjector;
import netflix.admin.AdminConfigImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Tomasz Bak
 */
public abstract class AbstractEurekaServer<C extends EurekaCommonConfig> extends DefaultLifecycleListener {

    private static final Logger logger = LoggerFactory.getLogger(AbstractEurekaServer.class);

    protected final C config;
    protected final String name;

    protected LifecycleInjector injector;

    protected AbstractEurekaServer(String name) {
        this.config = null;
        this.name = name;
    }

    protected AbstractEurekaServer(C config) {
        this.config = config;
        this.name = null;
    }

    public int getHttpServerPort() {
        EurekaHttpServer httpServer = injector.getInstance(EurekaHttpServer.class);
        if (httpServer != null) {
            return httpServer.serverPort();
        }
        return -1;
    }

    public int getShutdownPort() {
        EurekaShutdownService shutdownService = injector.getInstance(EurekaShutdownService.class);
        if (shutdownService != null) {
            return shutdownService.getShutdownPort();
        }
        return -1;
    }

    protected abstract Module getModule();

    public void start() {
        Module eurekaServerModule = new AbstractModule() {
            @Override
            protected void configure() {
                if (name != null) {  // TODO this is not clean. Should not need name or config
                    bind(String.class).annotatedWith(ApplicationLayer.class).toInstance(name);
                }

                // hack around adminConsole and need for archaius1 bridge
                if (config != null) {
                    ConfigurationManager.getConfigInstance().setProperty(
                            "netflix.platform.admin.resources.port", Integer.toString(config.getWebAdminPort()));
                }
            }
        };
        injector = Governator.createInjector(Modules.override(getModule()).with(eurekaServerModule));
    }

    public void waitTillShutdown() {
        try {
            injector.awaitTermination();
        } catch (Exception e) {
            logger.error("Server error", e);
        } finally {
            shutdown();
            logger.info("All services stopped; quitting");
        }
    }

    public void shutdown() {
        if (injector != null) {
            injector.shutdown();
        }
    }


    // hack to get around karyon web admin to make the admin port settable via code config
    @Singleton
    static class MyAdminContainerConfig extends AdminConfigImpl {

        private final Integer port;

        @Inject
        public MyAdminContainerConfig() {
            this.port = null;
        }

        public MyAdminContainerConfig(int port) {
            this.port = port;
        }

        @Override
        public int listenPort() {
            if (port != null) {
                return port;
            }

            return super.listenPort();
        }
    }
}
