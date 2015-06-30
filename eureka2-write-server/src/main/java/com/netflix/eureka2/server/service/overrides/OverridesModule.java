package com.netflix.eureka2.server.service.overrides;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.MapBinder;

/**
 * A Governator/Guice module for the override functionality.
 *
 * @author David Liu
 */
public class OverridesModule extends AbstractModule {
    @Override
    protected void configure() {

        bind(InstanceStatusOverridesSource.class).to(InMemoryStatusOverridesRegistry.class);
        bind(InstanceStatusOverridesView.class).to(InMemoryStatusOverridesRegistry.class);

        MapBinder<Integer, OverridesService> mapbinder = MapBinder.newMapBinder(binder(), Integer.class, OverridesService.class);
        // for ordering
        mapbinder.addBinding(0).to(InstanceStatusOverridesService.class);
    }
}
