package com.netflix.eureka2.metric.noop;

import com.netflix.eureka2.metric.server.BridgeChannelMetrics;
import com.netflix.eureka2.metric.server.BridgeServerMetricFactory;

/**
 * @author Tomasz Bak
 */
public class NoOpBridgeServerMetricFactory extends BridgeServerMetricFactory {
    @Override
    public BridgeChannelMetrics getBridgeChannelMetrics() {
        return NoOpBridgeChannelMetrics.INSTANCE;
    }
}
