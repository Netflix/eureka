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

package com.netflix.eureka.server.spi;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.ServiceLoader;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.netflix.eureka.server.audit.AuditService;
import com.netflix.eureka.server.audit.SimpleAuditService;
import com.netflix.governator.guice.BootstrapBinder;
import com.netflix.governator.guice.BootstrapModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads Eureka extensions using {@link java.util.ServiceLoader}. Eureka extension
 * to be discoverable must provide module implementation derived from {@link ExtAbstractModule},
 * that is installed in META-INF/services according to the {@link java.util.ServiceLoader} rules.
 *
 * @author Tomasz Bak
 */
public class ExtensionLoader {

    private static final Logger logger = LoggerFactory.getLogger(ExtensionLoader.class);

    private final ExtensionContext extensionContext;

    /**
     * If set to true, ignore discovered modules that are not runnable (see {@link ExtAbstractModule#isRunnable(ExtensionContext)}).
     * Otherwise fail applicaton bootstrap.
     */
    private final boolean ignoreNotRunnable;

    public ExtensionLoader(ExtensionContext extensionContext, boolean ignoreNotRunnable) {
        this.extensionContext = extensionContext;
        this.ignoreNotRunnable = ignoreNotRunnable;
    }

    public Module[] asModuleArray() {
        List<Module> moduleList = enableExtensions();
        return moduleList.toArray(new Module[moduleList.size()]);
    }

    // TODO: ExtensionContext not visible if we use BootstrapModule. Why?
    public BootstrapModule asBootstrapModule() {
        return new BootstrapModule() {
            @Override
            public void configure(BootstrapBinder binder) {
                for (Module m : enableExtensions()) {
                    binder.install(m);
                }
            }
        };
    }

    private List<Module> enableExtensions() {
        List<Module> moduleList = new ArrayList<>();

        // Discover and load whats available
        final EnumSet<StandardExtension> loadedStdExts = EnumSet.noneOf(StandardExtension.class);
        for (ExtAbstractModule m : ServiceLoader.load(ExtAbstractModule.class)) {
            if (m.isRunnable(extensionContext)) {
                logger.info("Loading module {}", m.getClass().getName());
                moduleList.add(m);
                if (m.standardExtension() != StandardExtension.Undefined) {
                    loadedStdExts.add(m.standardExtension());
                }
            } else if (!ignoreNotRunnable) {
                throw new IllegalArgumentException("Cannot initialize module " + m.getClass().getName());
            } else {
                logger.warn("Module {} not runnable; skipping it", m.getClass().getName());
            }
        }

        // Use defaults for standard extensions
        moduleList.add(new AbstractModule() {
            @Override
            protected void configure() {
                EnumSet<StandardExtension> missingExtensions = EnumSet.complementOf(loadedStdExts);
                for (StandardExtension ext : missingExtensions) {
                    if (ext.hasDefault()) {
                        logger.info("Binding default implementation for service {}", ext.getServiceInterface());
                        bind(ext.getServiceInterface()).toInstance(ext.createInstance());
                    }
                }
            }
        });

        return moduleList;
    }

    public enum StandardExtension {
        AuditServiceExt(true, AuditService.class) {
            @Override
            public Object createInstance() {
                return new SimpleAuditService();
            }
        },
        Undefined(false, null) {
            @Override
            public Object createInstance() {
                throw new IllegalStateException("Undefined extension cannot be created");
            }
        };

        private final boolean defaultAvailable;
        private final Class serviceInterface;

        StandardExtension(boolean defaultAvailable, Class<?> serviceInterface) {
            this.defaultAvailable = defaultAvailable;
            this.serviceInterface = serviceInterface;
        }

        public boolean hasDefault() {
            return defaultAvailable;
        }

        public Class getServiceInterface() {
            return serviceInterface;
        }

        public abstract Object createInstance();
    }
}
