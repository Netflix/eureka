package com.netflix.eureka2.health;

import java.util.List;

import rx.Observable;

/**
 * @author Tomasz Bak
 */
public interface EurekaHealthStatusAggregator extends HealthStatusProvider<EurekaHealthStatusAggregator> {
    Observable<List<HealthStatusProvider<?>>> components();
}
