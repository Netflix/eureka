package com.netflix.discovery.shared.transport.jersey2;

import javax.ws.rs.client.Client;

/**
 * @author David Liu
 */
public interface EurekaJersey2Client {

    Client getClient();

    /**
     * Clean up resources.
     */
    void destroyResources();
}
