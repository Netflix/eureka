package com.netflix.eureka2.metric.server;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.netflix.eureka2.metric.MessageConnectionMetrics;
import com.netflix.eureka2.metric.SpectatorMessageConnectionMetrics;
import com.netflix.spectator.api.ExtendedRegistry;

/**
 * @author Tomasz Bak
 */
@Singleton
public class SpectatorEurekaServerMetricFactory extends EurekaServerMetricFactory {
    private final SpectatorMessageConnectionMetrics replicationServerConnectionMetrics;
    private final SpectatorMessageConnectionMetrics registrationServerConnectionMetrics;
    private final SpectatorMessageConnectionMetrics discoveryServerConnectionMetrics;
    private final SpectatorServerInterestChannelMetrics interestChannelMetrics;

    @Inject
    public SpectatorEurekaServerMetricFactory(ExtendedRegistry registry) {
        this.replicationServerConnectionMetrics = new SpectatorMessageConnectionMetrics(registry, "replication");
        this.registrationServerConnectionMetrics = new SpectatorMessageConnectionMetrics(registry, "registration");
        this.discoveryServerConnectionMetrics = new SpectatorMessageConnectionMetrics(registry, "discovery");
        this.interestChannelMetrics = new SpectatorServerInterestChannelMetrics(registry);
    }
}
