package com.netflix.discovery;

/**
 * Marker interface for Eureka events
 * 
 * @see {@link EurekaEventListener}
 */
public interface EurekaEvent {
    /**
     * @return Time at which the event occurred
     */
    long getTimestamp();
}
