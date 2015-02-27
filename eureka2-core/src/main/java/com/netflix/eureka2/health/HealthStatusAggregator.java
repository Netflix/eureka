package com.netflix.eureka2.health;

import java.util.List;

import rx.Observable;

/**
 * @author Tomasz Bak
 */
public interface HealthStatusAggregator<SUBSYSTEM extends HealthStatusAggregator<SUBSYSTEM>>
        extends HealthStatusProvider<SUBSYSTEM> {
    Observable<List<HealthStatusProvider<?>>> components();
}
