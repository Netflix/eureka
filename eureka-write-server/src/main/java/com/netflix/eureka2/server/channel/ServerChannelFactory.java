package com.netflix.eureka2.server.channel;

import com.netflix.eureka2.channel.RegistrationChannel;

/**
 * Server channels factory.
 *
 * A channel instance is dedicated to a client connection and is stateful with respect to the operations that can be
 * performed on a service instance.
 *
 * @author Nitesh Kant
 */
public interface ServerChannelFactory extends InterestChannelFactory {

    /**
     * Returns a new {@link com.netflix.eureka2.channel.RegistrationChannel}.
     *
     * @return A new {@link com.netflix.eureka2.channel.RegistrationChannel}.
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
