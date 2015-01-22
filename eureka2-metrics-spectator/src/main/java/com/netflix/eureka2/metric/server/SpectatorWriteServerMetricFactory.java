package com.netflix.eureka2.metric.server;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.netflix.eureka2.metric.MessageConnectionMetrics;
import com.netflix.eureka2.metric.RegistrationChannelMetrics;
import com.netflix.eureka2.metric.SpectatorMessageConnectionMetrics;
import com.netflix.eureka2.metric.SpectatorRegistrationChannelMetrics;
import com.netflix.spectator.api.ExtendedRegistry;

/**
 * @author Tomasz Bak
 */
@Singleton
public class SpectatorWriteServerMetricFactory extends WriteServerMetricFactory {
    private final SpectatorMessageConnectionMetrics replicationSenderConnectionMetrics;
    private final SpectatorMessageConnectionMetrics replicationReceiverConnectionMetrics;
    private final SpectatorMessageConnectionMetrics registrationConnectionMetrics;
    private final SpectatorMessageConnectionMetrics discoveryConnectionMetrics;
    private final SpectatorRegistrationChannelMetrics registrationChannelMetrics;
    private final SpectatorReplicationChannelMetrics replicationChannelMetrics;
    private final SpectatorServerInterestChannelMetrics interestChannelMetrics;

    @Inject
    public SpectatorWriteServerMetricFactory(ExtendedRegistry registry) {
        this.replicationSenderConnectionMetrics = new SpectatorMessageConnectionMetrics(registry, "replicationSender");
        this.replicationReceiverConnectionMetrics = new SpectatorMessageConnectionMetrics(registry, "replicationReceiver");
        this.registrationConnectionMetrics = new SpectatorMessageConnectionMetrics(registry, "registration");
        this.discoveryConnectionMetrics = new SpectatorMessageConnectionMetrics(registry, "discovery");
        this.registrationChannelMetrics = new SpectatorRegistrationChannelMetrics(registry, "server");
        this.replicationChannelMetrics = new SpectatorReplicationChannelMetrics(registry, "server");
        this.interestChannelMetrics = new SpectatorServerInterestChannelMetrics(registry);
    }

    @Override
    public MessageConnectionMetrics getReplicationSenderConnectionMetrics() {
        return replicationSenderConnectionMetrics;
    }

    @Override
    public MessageConnectionMetrics getReplicationReceiverConnectionMetrics() {
        return replicationReceiverConnectionMetrics;
    }

    @Override
    public RegistrationChannelMetrics getRegistrationChannelMetrics() {
        return registrationChannelMetrics;
    }

    @Override
    public ReplicationChannelMetrics getReplicationChannelMetrics() {
        return replicationChannelMetrics;
    }

    @Override
    public MessageConnectionMetrics getRegistrationConnectionMetrics() {
        return registrationConnectionMetrics;
    }

    @Override
    public MessageConnectionMetrics getReplicationConnectionMetrics() {
        return replicationSenderConnectionMetrics;
    }

    @Override
    public MessageConnectionMetrics getDiscoveryConnectionMetrics() {
        return discoveryConnectionMetrics;
    }

    @Override
    public ServerInterestChannelMetrics getInterestChannelMetrics() {
        return interestChannelMetrics;
    }
}
