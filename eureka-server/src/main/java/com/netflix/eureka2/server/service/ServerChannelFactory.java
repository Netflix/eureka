package com.netflix.eureka2.server.service;

import com.netflix.eureka2.service.InterestChannel;
import com.netflix.eureka2.service.RegistrationChannel;

/**
 * Server channels factory.
 *
 * A channel instance is dedicated to a client connection and is stateful with respect to the operations that can be
 * performed on a service instance.
 *
 * @author Nitesh Kant
 */
public interface ServerChannelFactory {

    /**
     * Returns a new {@link com.netflix.eureka2.service.InterestChannel}.
     *
     * @return A new {@link com.netflix.eureka2.service.InterestChannel}.
     */
    InterestChannel newInterestChannel();

    /**
     * Returns a new {@link com.netflix.eureka2.service.RegistrationChannel}.
     *
     * @return A new {@link com.netflix.eureka2.service.RegistrationChannel}.
     */
    RegistrationChannel newRegistrationChannel();

    /**
     * Opens a new {@link ReplicationChannel}.
     *
     * @return The {@link ReplicationChannel}.
     */
    ReplicationChannel newReplicationChannel();

    /**
     * Release underlying resources.
     */
    void shutdown();
}
