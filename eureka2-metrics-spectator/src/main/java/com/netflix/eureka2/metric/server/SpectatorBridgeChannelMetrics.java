package com.netflix.eureka2.metric.server;

import com.netflix.eureka2.channel.BridgeChannel.STATE;
import com.netflix.eureka2.metric.AbstractStateMachineMetrics;
import com.netflix.spectator.api.ExtendedRegistry;

/**
 * @author Tomasz Bak
 */
public class SpectatorBridgeChannelMetrics extends AbstractStateMachineMetrics<STATE> implements BridgeChannelMetrics {
//    private final LongGauge totalCount;
//    private final LongGauge registerCount;
//    private final LongGauge updateCount;
//    private final LongGauge unregisterCount;

    public SpectatorBridgeChannelMetrics(ExtendedRegistry registry) {
        super(registry, "bridgeChannel", STATE.class);
//        totalCount = newLongGauge("totalCount");
//        registerCount = newLongGauge("registerCount");
//        updateCount = newLongGauge("updateCount");
//        unregisterCount = newLongGauge("unregisterCount");
//
//        register(totalCount, updateCount, registerCount, unregisterCount);
    }

    @Override
    public void setTotalCount(long n) {
//        totalCount.set(n);
    }

    @Override
    public void setRegisterCount(long n) {
//        registerCount.set(n);
    }

    @Override
    public void setUpdateCount(long n) {
//        updateCount.set(n);
    }

    @Override
    public void setUnregisterCount(long n) {
//        unregisterCount.set(n);
    }
}
