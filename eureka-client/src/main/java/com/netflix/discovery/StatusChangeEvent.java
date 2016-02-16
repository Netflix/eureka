package com.netflix.discovery;

import com.netflix.appinfo.InstanceInfo;

/**
 * Event containing the latest instance status information.  This event
 * is sent to the {@link com.netflix.eventbus.spi.EventBus} by {@link EurekaClient) whenever
 * a status change is identified from the remote Eureka server response.
 */
public class StatusChangeEvent extends DiscoveryEvent {
    private final InstanceInfo.InstanceStatus current;
    private final InstanceInfo.InstanceStatus previous;

    public StatusChangeEvent(InstanceInfo.InstanceStatus previous, InstanceInfo.InstanceStatus current) {
        super();
        this.current = current;
        this.previous = previous;
    }

    /**
     * Return the up current when the event was generated.
     * @return true if current is up or false for ALL other current values
     */
    public boolean isUp() {
        return this.current.equals(InstanceInfo.InstanceStatus.UP);
    }

    /**
     * @return The current at the time the event is generated.
     */
    public InstanceInfo.InstanceStatus getStatus() {
        return current;
    }

    /**
     * @return Return the client status immediately before the change
     */
    public InstanceInfo.InstanceStatus getPreviousStatus() {
        return previous;
    }

    @Override
    public String toString() {
        return "StatusChangeEvent [timestamp=" + getTimestamp() + ", current=" + current + ", previous="
                + previous + "]";
    }

}
