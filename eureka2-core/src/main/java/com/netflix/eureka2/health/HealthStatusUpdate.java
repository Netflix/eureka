package com.netflix.eureka2.health;

import com.netflix.eureka2.model.instance.InstanceInfo.Status;

/**
 * @author Tomasz Bak
 */
public class HealthStatusUpdate<SUBSYSTEM> {

    private final Status status;
    private final SubsystemDescriptor<SUBSYSTEM> description;

    public HealthStatusUpdate(Status status, SubsystemDescriptor<SUBSYSTEM> description) {
        this.status = status;
        this.description = description;
    }

    public Status getStatus() {
        return status;
    }

    public SubsystemDescriptor<SUBSYSTEM> getDescriptor() {
        return description;
    }

    @Override
    public String toString() {
        return "HealthStatusUpdate{" +
                "status=" + status +
                ", description=" + description +
                '}';
    }
}
