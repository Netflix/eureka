package com.netflix.eureka2.server.service.overrides;

import rx.Scheduler;

import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.server.service.overrides.LoadingOverridesRegistry.ExternalOverridesSource;

/**
 * @author David Liu
 */
public class LoadingOverridesRegistryTest extends OverridesRegistryTest {

    @Override
    OverridesRegistry getOverridesRegistry(Scheduler scheduler) {
        ExternalOverridesSource source = new InMemoryOverridesSource();
        return new LoadingOverridesRegistry(source, 30, TimeUnit.SECONDS, scheduler);
    }

}
