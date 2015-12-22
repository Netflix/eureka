package com.netflix.eureka2.metric.server;

import java.util.concurrent.atomic.AtomicInteger;

import com.netflix.eureka2.metric.AbstractStateMachineMetrics;
import com.netflix.spectator.api.ExtendedRegistry;

/**
 * @author Tomasz Bak
 */
public class SpectatorBridgeChannelMetrics extends AbstractStateMachineMetrics<BridgeChannelMetrics.STATE> implements BridgeChannelMetrics {
    private final AtomicInteger totalCount = new AtomicInteger();
    private final AtomicInteger registerCount = new AtomicInteger();
    private final AtomicInteger updateCount = new AtomicInteger();
    private final AtomicInteger unregisterCount = new AtomicInteger();

    public SpectatorBridgeChannelMetrics(ExtendedRegistry registry) {
        super(registry, "bridgeChannel", STATE.class);

        newGauge("totalCount", totalCount);
        newGauge("registerCount", registerCount);
        newGauge("updateCount", updateCount);
        newGauge("unregisterCount", unregisterCount);
    }

    @Override
    public void setTotalCount(int n) {
        totalCount.set(n);
    }

    @Override
    public void setRegisterCount(int n) {
        registerCount.set(n);
    }

    @Override
    public void setUpdateCount(int n) {
        updateCount.set(n);
    }

    @Override
    public void setUnregisterCount(int n) {
        unregisterCount.set(n);
    }
}
