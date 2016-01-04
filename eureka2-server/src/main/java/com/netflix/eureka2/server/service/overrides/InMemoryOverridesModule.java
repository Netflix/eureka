package com.netflix.eureka2.server.service.overrides;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;

/**
 * A Governator/Guice module for override functionality that uses an in-memory implementation
 * (useful for embedded/testing)
 *
 * @author David Liu
 */
public class InMemoryOverridesModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(InstanceStatusOverridesSource.class).to(InMemoryStatusOverridesRegistry.class).in(Scopes.SINGLETON);
        bind(InstanceStatusOverridesView.class).to(InMemoryStatusOverridesRegistry.class).in(Scopes.SINGLETON);
        bind(OverridesService.class).to(InstanceStatusOverridesService.class).in(Scopes.SINGLETON);
    }
}
