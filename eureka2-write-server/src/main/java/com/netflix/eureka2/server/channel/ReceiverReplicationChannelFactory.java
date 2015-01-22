package com.netflix.eureka2.server.channel;

import com.netflix.eureka2.metric.server.WriteServerMetricFactory;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.eviction.EvictionQueue;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.server.service.SelfInfoResolver;
import com.netflix.eureka2.transport.MessageConnection;

/**
 * @author David Liu
 */
public class ReceiverReplicationChannelFactory extends ServerChannelFactory<ReceiverReplicationChannel> {

    private final SelfInfoResolver selfInfoResolver;
    private final EvictionQueue evictionQueue;

    public ReceiverReplicationChannelFactory(SourcedEurekaRegistry<InstanceInfo> registry,
                                             MessageConnection connection,
                                             SelfInfoResolver selfInfoResolver,
                                             EvictionQueue evictionQueue,
                                             WriteServerMetricFactory metricFactory) {
        super(registry, connection, metricFactory);
        this.selfInfoResolver = selfInfoResolver;
        this.evictionQueue = evictionQueue;
    }

    @Override
    public ReceiverReplicationChannel newChannel() {
        return new ReceiverReplicationChannel(connection, selfInfoResolver, registry, evictionQueue,
                ((WriteServerMetricFactory) metricFactory).getReplicationChannelMetrics());
    }
}
