package com.netflix.eureka2.server.channel;

import com.netflix.eureka2.channel.ChannelFactory;
import com.netflix.eureka2.channel.InterestChannel;
import com.netflix.eureka2.registry.EurekaRegistryView;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.metric.server.EurekaServerMetricFactory;
import com.netflix.eureka2.transport.MessageConnection;

/**
 * @author Tomasz Bak
 */
public class InterestChannelFactory implements ChannelFactory<InterestChannel> {

    protected final EurekaRegistryView<InstanceInfo> registry;
    protected final MessageConnection connection;
    protected final EurekaServerMetricFactory metricFactory;

    public InterestChannelFactory(EurekaRegistryView<InstanceInfo> registry,
                                  MessageConnection connection,
                                  EurekaServerMetricFactory metricFactory) {
        this.registry = registry;
        this.connection = connection;
        this.metricFactory = metricFactory;
    }

    @Override
    public InterestChannel newChannel() {
        return new InterestChannelImpl(registry, connection, metricFactory.getInterestChannelMetrics());
    }

    @Override
    public void shutdown() {
    }
}
