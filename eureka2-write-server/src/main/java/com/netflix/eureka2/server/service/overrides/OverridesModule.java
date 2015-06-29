package com.netflix.eureka2.server.service.overrides;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;

/**
 * A Governator/Guice module for the override functionality.
 *
 * @author David Liu
 */
public class OverridesModule extends AbstractModule {
    @Override
    protected void configure() {
        // override this binding with more specific impls in non-local-testing environments
        bind(LoadingOverridesRegistry.ExternalOverridesSource.class).to(InMemoryOverridesSource.class).in(Scopes.SINGLETON);

        bind(OverridesService.class).to(OverridesServiceImpl.class);
        bind(OverridesRegistry.class).to(LoadingOverridesRegistry.class);
    }
}
