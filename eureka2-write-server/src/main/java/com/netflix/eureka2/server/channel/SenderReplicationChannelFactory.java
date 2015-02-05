package com.netflix.eureka2.server.channel;

import com.netflix.eureka2.channel.ChannelFactory;
import com.netflix.eureka2.channel.ReplicationChannel;
import com.netflix.eureka2.metric.server.WriteServerMetricFactory;
import com.netflix.eureka2.transport.EurekaTransports;
import com.netflix.eureka2.transport.TransportClient;

import java.net.InetSocketAddress;

/**
 * @author David Liu
 */
public class SenderReplicationChannelFactory implements ChannelFactory<ReplicationChannel> {

    private final TransportClient client;

    public SenderReplicationChannelFactory(InetSocketAddress address,
                                           EurekaTransports.Codec codec,
                                           WriteServerMetricFactory metricFactory) {
        this.client = new ReplicationTransportClient(address, codec, metricFactory.getReplicationSenderConnectionMetrics());
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
