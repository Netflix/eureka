package com.netflix.eureka2.testkit.embedded.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.util.Modules;
import com.netflix.eureka2.server.config.EurekaServerConfig;
import com.netflix.eureka2.testkit.netrouter.NetworkRouter;

/**
 * @author Tomasz Bak
 */

public abstract class EmbeddedServerBuilder<C extends EurekaServerConfig, B extends EmbeddedServerBuilder<C, B>> {

    protected C configuration;
    protected boolean ext;
    protected List<Class<? extends Module>> extensionModules;
    protected NetworkRouter networkRouter;
    protected boolean adminUI;
    private Map<Class<?>, Object> configurationOverrides;

    public B withConfiguration(C configuration) {
        this.configuration = configuration;
        return self();
    }

    public B withExt(boolean ext) {
        this.ext = ext;
        return self();
    }

    public B withExtensionModules(List<Class<? extends Module>> extensionModules) {
        this.extensionModules = extensionModules;
        return self();
    }

    public B withConfigurationOverrides(Map<Class<?>, Object> configurationOverrides) {
        this.configurationOverrides = configurationOverrides;
        return self();
    }

    public B withNetworkRouter(NetworkRouter networkRouter) {
        this.networkRouter = networkRouter;
        return self();
    }

    public B withAdminUI(boolean adminUI) {
        this.adminUI = adminUI;
        return self();
    }

    protected B self() {
        return (B) this;
    }

    protected Module combineWithExtensionModules(Module applicationModules) {
        List<Module> extModules = new ArrayList<>();
        if (extensionModules != null && !extensionModules.isEmpty()) {
            for (Class<? extends Module> moduleClass : extensionModules) {
                try {
                    extModules.add(moduleClass.newInstance());
                } catch (Exception e) {
                    throw new IllegalArgumentException("Cannot create Guice module from class " + moduleClass, e);
                }
            }
            return Modules.combine(applicationModules, Modules.combine(extModules));
        }
        return applicationModules;
    }

    protected Module combineWithConfigurationOverrides(Module applicationModules, List<Module> overrides) {
        if (configurationOverrides == null || configurationOverrides.isEmpty()) {
            return Modules.override(applicationModules).with(overrides);
        }
        List<Module> combinedOverrides = new ArrayList<>(overrides);
        combinedOverrides.add(new AbstractModule() {
            @Override
            protected void configure() {
                for (Map.Entry<Class<?>, Object> entry : configurationOverrides.entrySet()) {
                    Class key = entry.getKey();
                    bind(key).toInstance(entry.getValue());
                }
            }
        });
        return Modules.override(applicationModules).with(combinedOverrides);
    }
}
