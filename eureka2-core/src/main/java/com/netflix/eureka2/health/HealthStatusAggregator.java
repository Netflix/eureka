package com.netflix.eureka2.health;

import java.util.List;

import rx.Observable;

/**
 * @author Tomasz Bak
 */
public interface HealthStatusAggregator<STATUS extends Enum<STATUS>, SUBSYSTEM extends HealthStatusAggregator<STATUS, SUBSYSTEM>>
        extends HealthStatusProvider<STATUS, SUBSYSTEM> {
    Observable<List<HealthStatusProvider<?, ?>>> components();
}
