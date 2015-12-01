package com.netflix.eureka2.server.channel;

import com.netflix.eureka2.channel.ChannelFactory;
import com.netflix.eureka2.channel.ReplicationChannel;
import com.netflix.eureka2.config.EurekaTransportConfig;
import com.netflix.eureka2.metric.server.WriteServerMetricFactory;
import com.netflix.eureka2.transport.TransportClient;
import com.netflix.eureka2.model.Server;

/**
 * @author David Liu
 */
public class SenderReplicationChannelFactory implements ChannelFactory<ReplicationChannel> {

    private final TransportClient client;

    public SenderReplicationChannelFactory(EurekaTransportConfig config,
                                           Server address,
                                           WriteServerMetricFactory metricFactory) {
        this.client = new ReplicationTransportClient(config, address, metricFactory.getReplicationSenderConnectionMetrics());
    }

    @Override
    public ReplicationChannel newChannel() {
        return new SenderReplicationChannel(client,
                null // TODO we need separate metrics for send and receiver; now only receiver is taken care of
        );
    }

    @Override
    public void shutdown() {
        client.shutdown();
    }
}
