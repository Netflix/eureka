package com.netflix.eureka2.health;

import com.netflix.eureka2.registry.instance.InstanceInfo.Status;

/**
 * @author Tomasz Bak
 */
public class HealthStatusUpdate<STATUS extends Enum<STATUS>, SUBSYSTEM> {

    private final STATUS status;
    private final SubsystemDescriptor<STATUS, SUBSYSTEM> description;
    private final Status eurekaStatus;

    public HealthStatusUpdate(STATUS status,
                              SubsystemDescriptor<STATUS, SUBSYSTEM> description,
                              Status eurekaStatus) {
        this.status = status;
        this.description = description;
        this.eurekaStatus = eurekaStatus;
    }

    public STATUS getStatus() {
        return status;
    }

    public Status getEurekaStatus() {
        return eurekaStatus;
    }

    public SubsystemDescriptor<STATUS, SUBSYSTEM> getDescriptor() {
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
