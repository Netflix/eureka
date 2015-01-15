package com.netflix.eureka2.server.metric;

import javax.inject.Inject;
import javax.inject.Named;

import com.netflix.eureka2.metric.MessageConnectionMetrics;

/**
 * @author David Liu
 */
public class BridgeServerMetricFactory extends WriteServerMetricFactory {

    private final BridgeChannelMetrics bridgeChannelMetrics;

    @Inject
    public BridgeServerMetricFactory(
            @Named("registration") MessageConnectionMetrics registrationConnectionMetrics,
            @Named("replication") MessageConnectionMetrics replicationConnectionMetrics,
            @Named("discovery") MessageConnectionMetrics discoveryConnectionMetrics,
            @Named("clientRegistration") MessageConnectionMetrics registrationServerConnectionMetrics,
            @Named("clientDiscovery") MessageConnectionMetrics discoveryServerConnectionMetrics,
            @Named("clientReplication") MessageConnectionMetrics replicationServerConnectionMetrics,
            RegistrationChannelMetrics registrationChannelMetrics,
            ReplicationChannelMetrics replicationChannelMetrics,
            InterestChannelMetrics interestChannelMetrics,
            BridgeChannelMetrics bridgeChannelMetrics) {
        super(registrationConnectionMetrics, replicationConnectionMetrics, discoveryConnectionMetrics,
                replicationServerConnectionMetrics,
                registrationChannelMetrics, replicationChannelMetrics, interestChannelMetrics);
        this.bridgeChannelMetrics = bridgeChannelMetrics;
    }

    public BridgeChannelMetrics getBridgeChannelMetrics() {
        return bridgeChannelMetrics;
    }
}
