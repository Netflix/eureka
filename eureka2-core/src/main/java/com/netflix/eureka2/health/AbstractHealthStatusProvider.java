package com.netflix.eureka2.health;

import java.util.concurrent.atomic.AtomicReference;

import com.netflix.eureka2.registry.instance.InstanceInfo.Status;
import rx.Observable;
import rx.subjects.BehaviorSubject;

/**
 * @author Tomasz Bak
 */
public abstract class AbstractHealthStatusProvider<STATUS extends Enum<STATUS>, SUBSYSTEM> implements HealthStatusProvider<STATUS, SUBSYSTEM> {

    private final AtomicReference<STATUS> currentStatus;
    private final SubsystemDescriptor<STATUS, SUBSYSTEM> descriptor;

    private final BehaviorSubject<HealthStatusUpdate<STATUS, SUBSYSTEM>> healthSubject = BehaviorSubject.create();

    protected AbstractHealthStatusProvider(STATUS initialState, SubsystemDescriptor<STATUS, SUBSYSTEM> descriptor) {
        this.currentStatus = new AtomicReference<>();
        this.descriptor = descriptor;
        moveHealthTo(initialState);
    }

    @Override
    public Observable<HealthStatusUpdate<STATUS, SUBSYSTEM>> healthStatus() {
        return healthSubject;
    }

    public boolean moveHealthTo(STATUS newStatus) {
        if (currentStatus.getAndSet(newStatus) == newStatus) {
            return false;
        }
        healthSubject.onNext(new HealthStatusUpdate<STATUS, SUBSYSTEM>(newStatus, descriptor, toEurekaStatus(newStatus)));
        return true;
    }

    protected abstract Status toEurekaStatus(STATUS status);
}
