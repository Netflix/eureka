package com.netflix.eureka2.metric.noop;

import com.netflix.eureka2.channel.ReplicationChannel.STATE;
import com.netflix.eureka2.metric.server.ReplicationChannelMetrics;

/**
 * @author Tomasz Bak
 */
public class NoOpReplicationChannelMetrics implements ReplicationChannelMetrics {

    public static final NoOpReplicationChannelMetrics INSTANCE = new NoOpReplicationChannelMetrics();

    @Override
    public void incrementStateCounter(STATE state) {
    }

    @Override
    public void stateTransition(STATE from, STATE to) {
    }

    @Override
    public void decrementStateCounter(STATE state) {
    }
}
