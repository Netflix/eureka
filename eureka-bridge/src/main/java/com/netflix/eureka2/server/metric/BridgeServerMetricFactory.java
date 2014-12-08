package com.netflix.eureka2.server.metric;

import com.netflix.eureka2.metric.MessageConnectionMetrics;

import javax.inject.Inject;
import javax.inject.Named;

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
            EurekaServerRegistryMetrics eurekaServerRegistryMetrics,
            EvictionQueueMetrics evictionQueueMetrics,
            BridgeChannelMetrics bridgeChannelMetrics) {
        super(registrationConnectionMetrics, replicationConnectionMetrics, discoveryConnectionMetrics,
                registrationServerConnectionMetrics, discoveryServerConnectionMetrics, replicationServerConnectionMetrics,
                registrationChannelMetrics, replicationChannelMetrics, interestChannelMetrics,
                eurekaServerRegistryMetrics, evictionQueueMetrics);
        this.bridgeChannelMetrics = bridgeChannelMetrics;
    }

    public BridgeChannelMetrics getBridgeChannelMetrics() {
        return bridgeChannelMetrics;
    }
}
