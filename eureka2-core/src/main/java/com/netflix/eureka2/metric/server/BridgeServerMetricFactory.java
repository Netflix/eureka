package com.netflix.eureka2.metric.server;

import com.netflix.eureka2.metric.noop.NoOpBridgeServerMetricFactory;

/**
 * @author Tomasz Bak
 */
public abstract class BridgeServerMetricFactory extends WriteServerMetricFactory {

    private static volatile BridgeServerMetricFactory defaultFactory = new NoOpBridgeServerMetricFactory();

    public abstract BridgeChannelMetrics getBridgeChannelMetrics();

    public static BridgeServerMetricFactory bridgeServerMetrics() {
        return defaultFactory;
    }

    public static void setDefaultBridgeMetricFactory(BridgeServerMetricFactory newFactory) {
        defaultFactory = newFactory;
    }
}
