package com.netflix.eureka2.client.channel;

import com.netflix.eureka2.channel.ChannelFactory;
import com.netflix.eureka2.channel.ServiceChannel;
import com.netflix.eureka2.metric.client.EurekaClientMetricFactory;

/**
 * @author David Liu
 */
public abstract class ClientChannelFactory<T extends ServiceChannel> implements ChannelFactory<T> {

    protected final EurekaClientMetricFactory metricFactory;

    public ClientChannelFactory(EurekaClientMetricFactory metricFactory) {
        this.metricFactory = metricFactory;
    }
}
