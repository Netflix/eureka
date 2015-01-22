package com.netflix.eureka2.server.channel;

import com.netflix.eureka2.channel.RegistrationChannel;
import com.netflix.eureka2.metric.server.WriteServerMetricFactory;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.eviction.EvictionQueue;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.transport.MessageConnection;

/**
 * @author David Liu
 */
public class RegistrationChannelFactory extends ServerChannelFactory<RegistrationChannel> {

    private final EvictionQueue evictionQueue;

    public RegistrationChannelFactory(SourcedEurekaRegistry<InstanceInfo> registry,
                                      MessageConnection connection,
                                      EvictionQueue evictionQueue,
                                      WriteServerMetricFactory metricFactory) {
        super(registry, connection, metricFactory);
        this.evictionQueue = evictionQueue;
    }

    @Override
    public RegistrationChannel newChannel() {
        return new RegistrationChannelImpl(registry, evictionQueue, connection,
                ((WriteServerMetricFactory) metricFactory).getRegistrationChannelMetrics());
    }
}
