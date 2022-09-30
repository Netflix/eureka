package com.netflix.discovery.shared.transport.jersey3;

import jakarta.ws.rs.client.Client;

/**
 * @author David Liu
 */
public interface EurekaJersey3Client {

    Client getClient();

    /**
     * Clean up resources.
     */
    void destroyResources();
}
