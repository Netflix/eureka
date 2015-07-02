package com.netflix.eureka2.server.module;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.netflix.governator.auto.annotations.ConditionalOnMissingBinding;
import com.netflix.spectator.api.ExtendedRegistry;
import com.netflix.spectator.api.Spectator;

import javax.inject.Singleton;

/**
 * @author Tomasz Bak
 */
@ConditionalOnMissingBinding("com.netflix.spectator.api.ExtendedRegistry")
public class SpectatorDefaultMetricsModule extends AbstractModule {
    @Override
    protected void configure() {
    }

    @Provides
    @Singleton
    public ExtendedRegistry getExtendedRegistry() {
        return Spectator.registry();
    }
}
