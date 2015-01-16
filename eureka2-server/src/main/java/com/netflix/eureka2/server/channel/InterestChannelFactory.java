package com.netflix.eureka2.server.channel;

import com.netflix.eureka2.channel.InterestChannel;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.server.metric.EurekaServerMetricFactory;
import com.netflix.eureka2.transport.MessageConnection;

/**
 * @author Tomasz Bak
 */
public class InterestChannelFactory extends ServerChannelFactory<InterestChannel> {

    public InterestChannelFactory(SourcedEurekaRegistry<InstanceInfo> registry,
                                  MessageConnection connection,
                                  EurekaServerMetricFactory metricFactory) {
        super(registry, connection, metricFactory);
    }

    @Override
    public InterestChannel newChannel() {
        return new InterestChannelImpl(registry, connection, metricFactory.getInterestChannelMetrics());
    }
}
