package com.netflix.eureka2.metric.server;

import com.netflix.eureka2.channel.BridgeChannel.STATE;
import com.netflix.eureka2.metric.StateMachineMetrics;

/**
 * @author David Liu
 */
public interface BridgeChannelMetrics extends StateMachineMetrics<STATE> {

    void setTotalCount(int n);

    void setRegisterCount(int n);

    void setUpdateCount(int n);

    void setUnregisterCount(int n);
}
