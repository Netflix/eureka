package com.netflix.eureka2.server.service.replication;

/**
 * @author David Liu
 */
public interface ReplicationHandler {

    void startReplication();

    void shutdown();
}
