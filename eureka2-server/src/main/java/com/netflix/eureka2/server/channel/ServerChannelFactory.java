package com.netflix.eureka2.server.channel;

import com.netflix.eureka2.channel.ChannelFactory;
import com.netflix.eureka2.channel.ServiceChannel;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.metric.server.EurekaServerMetricFactory;
import com.netflix.eureka2.transport.MessageConnection;
import rx.Observable;
import rx.Subscriber;

/**
 * Abstract channel that provides some common basics for server channel factories
 *
 * @author David Liu
 */
public abstract class ServerChannelFactory<T extends ServiceChannel> implements ChannelFactory<T> {

    protected final SourcedEurekaRegistry<InstanceInfo> registry;
    protected final MessageConnection connection;
    protected final EurekaServerMetricFactory metricFactory;

    public ServerChannelFactory(SourcedEurekaRegistry<InstanceInfo> registry,
                                MessageConnection connection,
                                EurekaServerMetricFactory metricFactory) {
        this.registry = registry;
        this.connection = connection;
        this.metricFactory = metricFactory;
    }

    @Override
    public void shutdown() {
        // server side there is nothing to shutdown
    }
}
