package com.netflix.discovery;

public abstract class AbstractEurekaEvent implements EurekaEvent {
    // System time when the event happened
    private final long timestamp;
    
    protected AbstractEurekaEvent() {
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * @return Return the system time in milliseconds when the event happened.
     */
    @Override
    public final long getTimestamp() {
        return this.timestamp;
    }
}
