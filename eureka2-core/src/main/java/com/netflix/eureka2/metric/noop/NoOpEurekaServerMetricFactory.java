package com.netflix.eureka2.metric.noop;

import com.netflix.eureka2.metric.MessageConnectionMetrics;
import com.netflix.eureka2.metric.server.EurekaServerMetricFactory;
import com.netflix.eureka2.metric.server.ServerInterestChannelMetrics;

/**
 * @author Tomasz Bak
 */
public class NoOpEurekaServerMetricFactory extends EurekaServerMetricFactory {

    @Override
    public MessageConnectionMetrics getRegistrationConnectionMetrics() {
        return NoOpMessageConnectionMetrics.INSTANCE;
    }

    @Override
    public MessageConnectionMetrics getReplicationConnectionMetrics() {
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
