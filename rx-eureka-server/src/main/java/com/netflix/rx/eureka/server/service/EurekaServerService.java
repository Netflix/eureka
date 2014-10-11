package com.netflix.rx.eureka.server.service;

import com.netflix.rx.eureka.service.EurekaService;

/**
 * An extension of {@link com.netflix.rx.eureka.service.EurekaService} for eureka servers.
 *
 * A service instance is dedicated to a client connection and is stateful with respect to the operations that can be
 * performed on a service instance.
 *
 * @author Nitesh Kant
 */
public interface EurekaServerService extends EurekaService {

    /**
     * Opens a new {@link ReplicationChannel}.
     *
     * @return The {@link ReplicationChannel}.
     */
    ReplicationChannel newReplicationChannel();
}
