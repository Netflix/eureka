package com.netflix.eureka2.client.channel;

import com.netflix.eureka2.channel.RegistrationChannel;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.client.transport.TransportClients;
import com.netflix.eureka2.config.EurekaTransportConfig;
import com.netflix.eureka2.metric.client.EurekaClientMetricFactory;
import com.netflix.eureka2.transport.TransportClient;

/**
 * @author David Liu
 */
public class RegistrationChannelFactory extends ClientChannelFactory<RegistrationChannel> {

    private final TransportClient transport;

    public RegistrationChannelFactory(EurekaTransportConfig config,
                                      ServerResolver resolver,
                                      EurekaClientMetricFactory metricFactory) {
        this(TransportClients.newTcpRegistrationClient(config, resolver, metricFactory), metricFactory);
    }

    public RegistrationChannelFactory(TransportClient transport,
                                      EurekaClientMetricFactory metricFactory) {
        super(metricFactory);
        this.transport = transport;
    }

    @Override
    public RegistrationChannel newChannel() {
        RegistrationChannel baseChannel = new RegistrationChannelImpl(transport, metricFactory.getRegistrationChannelMetrics());
        return new RegistrationChannelInvoker(baseChannel, metricFactory.getSerializedTaskInvokerMetrics(RegistrationChannelInvoker.class));
    }

    @Override
    public void shutdown() {
        transport.shutdown();
    }
}
