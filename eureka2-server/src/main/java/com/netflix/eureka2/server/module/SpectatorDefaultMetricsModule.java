package com.netflix.eureka2.server.module;

import com.google.inject.AbstractModule;
import com.netflix.governator.auto.annotations.ConditionalOnMissingBinding;
import com.netflix.spectator.api.ExtendedRegistry;
import com.netflix.spectator.api.Spectator;

/**
 * @author Tomasz Bak
 */
@ConditionalOnMissingBinding("com.netflix.spectator.api.ExtendedRegistry")
public class SpectatorDefaultMetricsModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(ExtendedRegistry.class).toInstance(Spectator.registry());
    }
}
