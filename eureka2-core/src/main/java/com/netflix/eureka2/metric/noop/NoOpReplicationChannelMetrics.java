package com.netflix.eureka2.metric.noop;

import com.netflix.eureka2.metric.server.ReplicationChannelMetrics;

/**
 * @author Tomasz Bak
 */
public class NoOpReplicationChannelMetrics implements ReplicationChannelMetrics {

    public static final NoOpReplicationChannelMetrics INSTANCE = new NoOpReplicationChannelMetrics();
}
