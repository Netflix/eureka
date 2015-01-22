package com.netflix.eureka2.metric.noop;

import com.netflix.eureka2.channel.BridgeChannel.STATE;
import com.netflix.eureka2.metric.server.BridgeChannelMetrics;

/**
 * @author Tomasz Bak
 */
public class NoOpBridgeChannelMetrics implements BridgeChannelMetrics {

    public static final NoOpBridgeChannelMetrics INSTANCE = new NoOpBridgeChannelMetrics();

    @Override
    public void setTotalCount(long n) {
    }

    @Override
    public void setRegisterCount(long n) {
    }

    @Override
    public void setUpdateCount(long n) {
    }

    @Override
    public void setUnregisterCount(long n) {
    }

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
