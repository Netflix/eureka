package com.netflix.eureka2.metric.server;

import com.netflix.eureka2.channel.BridgeChannel.STATE;
import com.netflix.eureka2.metric.StateMachineMetrics;

/**
 * @author David Liu
 */
public interface BridgeChannelMetrics extends StateMachineMetrics<STATE> {

    void setTotalCount(long n);

    void setRegisterCount(long n);

    void setUpdateCount(long n);

    void setUnregisterCount(long n);
}
