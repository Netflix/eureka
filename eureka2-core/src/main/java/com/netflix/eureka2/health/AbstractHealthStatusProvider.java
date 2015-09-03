package com.netflix.eureka2.health;

import java.util.concurrent.atomic.AtomicReference;

import com.netflix.eureka2.model.instance.InstanceInfo.Status;
import rx.Observable;
import rx.subjects.BehaviorSubject;

/**
 * @author Tomasz Bak
 */
public abstract class AbstractHealthStatusProvider<SUBSYSTEM> implements HealthStatusProvider<SUBSYSTEM> {

    private final AtomicReference<Status> currentStatus;
    private final SubsystemDescriptor<SUBSYSTEM> descriptor;

    private final BehaviorSubject<HealthStatusUpdate<SUBSYSTEM>> healthSubject = BehaviorSubject.create();

    protected AbstractHealthStatusProvider(Status initialState, SubsystemDescriptor<SUBSYSTEM> descriptor) {
        this.currentStatus = new AtomicReference<>();
        this.descriptor = descriptor;
        moveHealthTo(initialState);
    }

    @Override
    public Observable<HealthStatusUpdate<SUBSYSTEM>> healthStatus() {
        return healthSubject;
    }

    public boolean moveHealthTo(Status newStatus) {
        if (currentStatus.getAndSet(newStatus) == newStatus) {
            return false;
        }
        healthSubject.onNext(new HealthStatusUpdate<SUBSYSTEM>(newStatus, descriptor));
        return true;
    }
}
