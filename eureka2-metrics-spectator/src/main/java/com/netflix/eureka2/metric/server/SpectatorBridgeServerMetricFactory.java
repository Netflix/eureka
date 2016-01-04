package com.netflix.eureka2.metric.server;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.netflix.eureka2.Names;
import com.netflix.eureka2.metric.SerializedTaskInvokerMetrics;
import com.netflix.eureka2.metric.SpectatorMessageConnectionMetrics;
import com.netflix.eureka2.metric.SpectatorRegistrationChannelMetrics;
import com.netflix.eureka2.metric.SpectatorSerializedTaskInvokerMetrics;
import com.netflix.spectator.api.ExtendedRegistry;

/**
 * @author Tomasz Bak
 */
@Singleton
public class SpectatorBridgeServerMetricFactory extends BridgeServerMetricFactory {

    private final SpectatorBridgeChannelMetrics bridgeChannelMetrics;
    private final SpectatorMessageConnectionMetrics replicationSenderConnectionMetrics;
    private final SpectatorMessageConnectionMetrics replicationReceiverConnectionMetrics;
    private final SpectatorMessageConnectionMetrics registrationConnectionMetrics;
    private final SpectatorRegistrationChannelMetrics registrationChannelMetrics;
    private final SpectatorServerInterestChannelMetrics interestChannelMetrics;
    private final SerializedTaskInvokerMetrics overrideServiceTaskInvokerMetrics;

    @Inject
    public SpectatorBridgeServerMetricFactory(ExtendedRegistry registry) {
        this.bridgeChannelMetrics = new SpectatorBridgeChannelMetrics(registry);
        this.replicationSenderConnectionMetrics = new SpectatorMessageConnectionMetrics(registry, "replicationSender");
        this.replicationReceiverConnectionMetrics = new SpectatorMessageConnectionMetrics(registry, "replicationReceiver");
        this.registrationConnectionMetrics = new SpectatorMessageConnectionMetrics(registry, "registration");
        this.registrationChannelMetrics = new SpectatorRegistrationChannelMetrics(registry, Names.EUREKA_SERVICE);
        this.interestChannelMetrics = new SpectatorServerInterestChannelMetrics(registry);
        this.overrideServiceTaskInvokerMetrics = new SpectatorSerializedTaskInvokerMetrics(registry, "overrideService");
    }

    @Override
    public BridgeChannelMetrics getBridgeChannelMetrics() {
        return bridgeChannelMetrics;
    }
}
