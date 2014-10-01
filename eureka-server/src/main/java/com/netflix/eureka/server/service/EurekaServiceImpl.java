package com.netflix.eureka.server.service;

import com.netflix.eureka.registry.EurekaRegistry;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.server.transport.ClientConnection;
import com.netflix.eureka.service.InterestChannel;
import com.netflix.eureka.service.RegistrationChannel;

/**
 * An implementation of {@link EurekaServerService} associated with strictly one {@link ClientConnection}
 *
 * <h2>Thread safety</h2>
 *
 * See {@link EurekaServerService} for details. This service assumes sequential (single threaded) invocations.
 *
 * @author Nitesh Kant
 */
public class EurekaServiceImpl implements EurekaServerService {

    private final EurekaRegistry<InstanceInfo> registry;
    private final ClientConnection connection;

    public EurekaServiceImpl(EurekaRegistry<InstanceInfo> registry, ClientConnection connection) {
        this.registry = registry;
        this.connection = connection;
    }

    @Override
    public InterestChannel newInterestChannel() {
        return new InterestChannelImpl(registry, connection);
    }

    @Override
    public RegistrationChannel newRegistrationChannel() {
        return new RegistrationChannelImpl(registry, connection);
    }

    @Override
    public ReplicationChannel newReplicationChannel() {
        return new ReplicationChannelImpl(connection, registry);
    }

    @Override
    public void shutdown() {
        connection.shutdown();
    }
}
