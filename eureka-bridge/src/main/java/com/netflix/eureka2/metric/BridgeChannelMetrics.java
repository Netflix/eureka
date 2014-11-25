package com.netflix.eureka2.metric;

import com.netflix.servo.monitor.LongGauge;

/**
 * @author David Liu
 */
public class BridgeChannelMetrics extends EurekaMetrics {

    private final LongGauge totalCount;
    private final LongGauge registerCount;
    private final LongGauge updateCount;
    private final LongGauge unregisterCount;

    public BridgeChannelMetrics() {
        super("bridgeChannel");
        totalCount = newLongGauge("totalCount");
        registerCount = newLongGauge("registerCount");
        updateCount = newLongGauge("updateCount");
        unregisterCount = newLongGauge("unregisterCount");

        register(totalCount);
        register(updateCount);
        register(registerCount);
        register(unregisterCount);
    }

    public void setTotalCount(long n) {
        totalCount.set(n);
    }

    public void setRegisterCount(long n) {
        registerCount.set(n);
    }

    public void setUpdateCount(long n) {
        updateCount.set(n);
    }

    public void setUnregisterCount(long n) {
        unregisterCount.set(n);
    }
}
