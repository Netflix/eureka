package com.netflix.eureka2.health;

import rx.Observable;

/**
 * This interface shall be implemented by all components/subsystems managing their own
 * health status.
 *
 * @author Tomasz Bak
 */
public interface HealthStatusProvider<STATUS extends Enum<STATUS>, SUBSYSTEM> {

    /**
     * Returns an observable of health status changes. The returned observable shall never
     * emit an error, and should complete when the subsystem is shut down.
     */
    Observable<HealthStatusUpdate<STATUS, SUBSYSTEM>> healthStatus();
}
