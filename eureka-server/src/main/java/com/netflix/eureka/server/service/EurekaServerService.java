package com.netflix.eureka.server.service;

import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.service.EurekaService;

/**
 * An extension of {@link com.netflix.eureka.service.EurekaService} for eureka servers.
 *
 * A service instance is dedicated to a client connection and is stateful with respect to the operations that can be
 * performed on a service instance.
 *
 * @author Nitesh Kant
 */
public interface EurekaServerService extends EurekaService {

    /**
     * Opens a new {@link ReplicationChannel} with the passed {@code sourceServer} as the source of this replication
     * channel.
     *
     * @param sourceServer The source server which is the owner of the data flowing on this channel.
     *
     * @return The {@link ReplicationChannel}.
     */
    ReplicationChannel newReplicationChannel(InstanceInfo sourceServer);

}
