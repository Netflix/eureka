package com.netflix.eureka2.config;

/**
 * Configuration for the transport layer that applies to all channels created
 *
 * @author David Liu
 */
public interface EurekaTransportConfig {

    /**
     * Specify the heartbeat interval both to send to connecting endpoints, and also to expect from the connecting
     * endpoint.
     *
     * @return the heartbeat interval in milliseconds
     */
    long getHeartbeatIntervalMs();

    /**
     * Specify the baseline time for all connections to automatically terminate with a timeout.
     * Note that some amounts of randomisation may be applied to this baseline time at actual application.
     *
     * Eureka connections all have a fixed lifetime and should terminate themselves with a timeout. Higher level
     * constructs are responsible for reestablishing these connection.
     *
     * @return the timeout period in milliseconds
     */
    long getConnectionAutoTimeoutMs();
}
