package com.netflix.eureka2.testkit.netrouter;

/**
 * Network connector for Eureka servers and clients with capability to inject network level
 * errors into the test system.
 *
 * @author Tomasz Bak
 */
public interface NetworkRouter {

    /**
     * Create bridge server endpoint connecting to the given target server.
     * By default the connectivity is enabled with no restrictions.
     */
    int bridgeTo(int targetPort);

    /**
     * Shutdown and remove a bridge server belonging to the given target port. This also terminates
     * all client connections.
     */
    void removeBridgeTo(int targetPort);

    /**
     * A link between two servers with capabilities to inject different transmission properties
     * (throughput, data loss, etc).
     */
    NetworkLink getLinkTo(int port);

    /**
     * Shutdown all bridge servers.
     */
    void shutdown();
}
