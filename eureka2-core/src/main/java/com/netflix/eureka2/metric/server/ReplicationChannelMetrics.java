package com.netflix.eureka2.metric.server;

import com.netflix.eureka2.channel.ReplicationChannel.STATE;
import com.netflix.eureka2.metric.StateMachineMetrics;

/**
 * @author Tomasz Bak
 */
public interface ReplicationChannelMetrics extends StateMachineMetrics<STATE> {
}
