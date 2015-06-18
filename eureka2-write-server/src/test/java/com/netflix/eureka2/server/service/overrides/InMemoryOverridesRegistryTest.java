package com.netflix.eureka2.server.service.overrides;

import rx.Scheduler;

import java.util.concurrent.TimeUnit;

/**
 * @author David Liu
 */
public class InMemoryOverridesRegistryTest extends OverridesRegistryTest {

    @Override
    OverridesRegistry getOverridesRegistry(Scheduler scheduler) {
        return new InMemoryOverridesRegistry(30, TimeUnit.SECONDS, scheduler);
    }

}
