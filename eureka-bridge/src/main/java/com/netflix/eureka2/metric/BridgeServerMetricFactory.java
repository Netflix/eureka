package com.netflix.eureka2.metric;

import com.netflix.eureka2.server.metric.WriteServerMetricFactory;
import com.netflix.eureka2.server.registry.EurekaServerRegistryMetrics;
import com.netflix.eureka2.server.registry.eviction.EvictionQueueMetrics;
import com.netflix.eureka2.server.service.BridgeChannelMetrics;
import com.netflix.eureka2.server.service.InterestChannelMetrics;
import com.netflix.eureka2.server.service.RegistrationChannelMetrics;
import com.netflix.eureka2.server.service.ReplicationChannelMetrics;
import com.netflix.eureka2.transport.base.MessageConnectionMetrics;

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
            SerializedTaskInvokerMetrics registryTaskInvokerMetrics,
            BridgeChannelMetrics bridgeChannelMetrics) {
        super(registrationConnectionMetrics, replicationConnectionMetrics, discoveryConnectionMetrics,
                registrationServerConnectionMetrics, discoveryServerConnectionMetrics, replicationServerConnectionMetrics,
                registrationChannelMetrics, replicationChannelMetrics, interestChannelMetrics,
                eurekaServerRegistryMetrics, evictionQueueMetrics, registryTaskInvokerMetrics);
        this.bridgeChannelMetrics = bridgeChannelMetrics;
    }

    public BridgeChannelMetrics getBridgeChannelMetrics() {
        return bridgeChannelMetrics;
    }
}
