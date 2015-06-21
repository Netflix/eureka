package com.netflix.eureka2.server.module;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.netflix.eureka2.server.spi.ExtAbstractModule;
import com.netflix.eureka2.server.spi.ExtensionContext;
import com.netflix.eureka2.server.spi.ExtensionLoader;

/**
 * @author David Liu
 */
public class EurekaExtensionModule extends AbstractModule {

    private final ExtAbstractModule.ServerType type;

    public EurekaExtensionModule(ExtAbstractModule.ServerType type) {
        this.type = type;
    }

    @Override
    protected void configure() {
        // extension
        bind(ExtensionContext.class).asEagerSingleton();
        for (Module m : new ExtensionLoader().asModuleArray(type)) {
            install(m);
        }
    }
}
