package com.netflix.eureka2.metric.noop;

import com.netflix.eureka2.metric.MessageConnectionMetrics;
import com.netflix.eureka2.metric.RegistrationChannelMetrics;
import com.netflix.eureka2.metric.server.BridgeChannelMetrics;
import com.netflix.eureka2.metric.server.BridgeServerMetricFactory;
import com.netflix.eureka2.metric.server.ReplicationChannelMetrics;
import com.netflix.eureka2.metric.server.ServerInterestChannelMetrics;

/**
 * @author Tomasz Bak
 */
public class NoOpBridgeServerMetricFactory extends BridgeServerMetricFactory {
    @Override
    public BridgeChannelMetrics getBridgeChannelMetrics() {
        return NoOpBridgeChannelMetrics.INSTANCE;
    }

    @Override
    public MessageConnectionMetrics getReplicationSenderConnectionMetrics() {
        return NoOpMessageConnectionMetrics.INSTANCE;
    }

    @Override
    public MessageConnectionMetrics getReplicationReceiverConnectionMetrics() {
        return NoOpMessageConnectionMetrics.INSTANCE;
    }

    @Override
    public RegistrationChannelMetrics getRegistrationChannelMetrics() {
        return NoOpRegistrationChannelMetrics.INSTANCE;
    }

    @Override
    public ReplicationChannelMetrics getReplicationChannelMetrics() {
        return NoOpReplicationChannelMetrics.INSTANCE;
    }

    @Override
    public MessageConnectionMetrics getRegistrationConnectionMetrics() {
        return NoOpMessageConnectionMetrics.INSTANCE;
    }

    @Override
    public MessageConnectionMetrics getDiscoveryConnectionMetrics() {
        return NoOpMessageConnectionMetrics.INSTANCE;
    }

    @Override
    public ServerInterestChannelMetrics getInterestChannelMetrics() {
        return NoOpServerInterestChannelMetrics.INSTANCE;
    }
}
