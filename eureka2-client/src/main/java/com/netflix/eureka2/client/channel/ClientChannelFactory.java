package com.netflix.eureka2.client.channel;

import com.netflix.eureka2.channel.ChannelFactory;
import com.netflix.eureka2.channel.ServiceChannel;
import com.netflix.eureka2.client.metric.EurekaClientMetricFactory;

/**
 * @author David Liu
 */
public abstract class ClientChannelFactory<T extends ServiceChannel> implements ChannelFactory<T> {

    protected final long retryInitialDelayMs;

    protected final EurekaClientMetricFactory metricFactory;

    public ClientChannelFactory(long retryInitialDelayMs,
                                EurekaClientMetricFactory metricFactory) {
        this.retryInitialDelayMs = retryInitialDelayMs;
        this.metricFactory = metricFactory;
    }

}
