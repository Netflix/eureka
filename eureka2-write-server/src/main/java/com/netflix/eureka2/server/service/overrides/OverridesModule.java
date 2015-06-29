package com.netflix.eureka2.server.service.overrides;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;

/**
 * A Governator/Guice module for the override functionality.
 *
 * @author David Liu
 */
public class OverridesModule extends AbstractModule {
    @Override
    protected void configure() {
        Multibinder<OverridesService> multibinder = Multibinder.newSetBinder(binder(), OverridesService.class);
        multibinder.addBinding().to(OverridesServiceImpl.class);

        // override this binding with more specific impls in non-local-testing environments
        bind(OverridesRegistry.class).to(InMemoryOverridesRegistry.class);
    }
}
