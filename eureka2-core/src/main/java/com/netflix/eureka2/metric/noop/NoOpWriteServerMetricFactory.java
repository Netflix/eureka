package com.netflix.eureka2.metric.noop;

import com.netflix.eureka2.metric.MessageConnectionMetrics;
import com.netflix.eureka2.metric.RegistrationChannelMetrics;
import com.netflix.eureka2.metric.SerializedTaskInvokerMetrics;
import com.netflix.eureka2.metric.server.ReplicationChannelMetrics;
import com.netflix.eureka2.metric.server.ServerInterestChannelMetrics;
import com.netflix.eureka2.metric.server.WriteServerMetricFactory;

/**
 * @author Tomasz Bak
 */
public class NoOpWriteServerMetricFactory extends WriteServerMetricFactory {
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

    @Override
    public SerializedTaskInvokerMetrics getOverrideServiceTaskInvokerMetrics() {
        return NoOpSerializedTaskInvokerMetrics.INSTANCE;
    }
}
