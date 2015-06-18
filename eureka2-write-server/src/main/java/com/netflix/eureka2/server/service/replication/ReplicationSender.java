package com.netflix.eureka2.server.service.replication;

/**
 * @author David Liu
 */
public interface ReplicationSender {

    void startReplication();

    void shutdown();
}
