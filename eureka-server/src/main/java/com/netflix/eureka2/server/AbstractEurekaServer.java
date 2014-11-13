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

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.netflix.adminresources.resources.KaryonWebAdminModule;
import com.netflix.governator.guice.LifecycleInjector;
import com.netflix.governator.guice.LifecycleInjectorBuilder;
import com.netflix.governator.guice.LifecycleInjectorBuilderSuite;
import com.netflix.governator.lifecycle.LifecycleManager;
import com.netflix.karyon.archaius.ArchaiusSuite;
import com.netflix.karyon.health.AlwaysHealthyHealthCheck;
import com.netflix.karyon.health.HealthCheckHandler;
import com.netflix.karyon.health.HealthCheckInvocationStrategy;
import com.netflix.karyon.health.SyncHealthCheckInvocationStrategy;
import com.netflix.karyon.servo.KaryonServoModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 *
 * @author Tomasz Bak
 */
public abstract class AbstractEurekaServer<C extends EurekaBootstrapConfig> {

    private static final Logger logger = LoggerFactory.getLogger(AbstractEurekaServer.class);

    protected final C config;
    protected final String name;

    protected Injector injector;
    private LifecycleManager lifecycleManager;

    protected AbstractEurekaServer(String name) {
        this.config = null;
        this.name = name;
    }

    protected AbstractEurekaServer(C config) {
        this.config = config;
        this.name = null;
    }

    public void start() throws Exception {
        List<LifecycleInjectorBuilderSuite> suites = new ArrayList<>();
        if (config == null) {
            suites.add(new ArchaiusSuite(name));
        }
        suites.add(KaryonWebAdminModule.asSuite());

        // TODO: replace fake health check with a real one.
        suites.add(new LifecycleInjectorBuilderSuite() {
            @Override
            public void configure(LifecycleInjectorBuilder builder) {
                builder.withAdditionalModules(new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(HealthCheckHandler.class).to(AlwaysHealthyHealthCheck.class).asEagerSingleton();
                        bind(HealthCheckInvocationStrategy.class).to(SyncHealthCheckInvocationStrategy.class).asEagerSingleton();
                    }
                });
            }
        });

        suites.add(KaryonServoModule.asSuite());
        additionalModules(suites);
        injector = LifecycleInjector.bootstrap(
                this.getClass(),
                suites.toArray(new LifecycleInjectorBuilderSuite[suites.size()]));
        startLifecycleManager();
    }

    protected abstract void additionalModules(List<LifecycleInjectorBuilderSuite> suites);

    private void startLifecycleManager() throws Exception {
        lifecycleManager = injector.getInstance(LifecycleManager.class);
        lifecycleManager.start();
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
        if (lifecycleManager != null) {
            lifecycleManager.close();
        }
    }

}
