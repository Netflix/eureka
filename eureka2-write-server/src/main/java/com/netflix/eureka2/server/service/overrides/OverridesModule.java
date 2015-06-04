package com.netflix.eureka2.server.service.overrides;

import com.google.inject.AbstractModule;

/**
 * A Governator/Guice module for the override functionality.
 *
 * @author David Liu
 */
public class OverridesModule extends AbstractModule {
    @Override
    protected void configure() {

        bind(OverridesService.class).to(OverridesServiceImpl.class);

        // override this binding with more specific impls in non-local-testing environments
        bind(OverridesRegistry.class).to(InMemoryOverridesRegistry.class);
    }
}
