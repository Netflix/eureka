package com.netflix.eureka2.server.channel;

import com.netflix.eureka2.channel.ChannelFactory;
import com.netflix.eureka2.channel.RegistrationChannel;
import com.netflix.eureka2.metric.server.EurekaServerMetricFactory;
import com.netflix.eureka2.metric.server.WriteServerMetricFactory;
import com.netflix.eureka2.registry.EurekaRegistrationProcessor;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.transport.MessageConnection;

/**
 * @author David Liu
 */
public class RegistrationChannelFactory implements ChannelFactory<RegistrationChannel> {

    private final MessageConnection connection;
    private final EurekaRegistrationProcessor<InstanceInfo> registrationProcessor;
    private final EurekaServerMetricFactory metricFactory;

    public RegistrationChannelFactory(EurekaRegistrationProcessor<InstanceInfo> registrationProcessor,
                                      MessageConnection connection,
                                      WriteServerMetricFactory metricFactory) {
        this.registrationProcessor = registrationProcessor;
        this.connection = connection;
        this.metricFactory = metricFactory;
    }

    @Override
    public RegistrationChannel newChannel() {
        return new RegistrationChannelImpl(registrationProcessor, connection,
                ((WriteServerMetricFactory) metricFactory).getRegistrationChannelMetrics());
    }

    @Override
    public void shutdown() {
    }
}
