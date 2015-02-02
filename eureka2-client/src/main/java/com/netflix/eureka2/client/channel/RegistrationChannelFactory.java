package com.netflix.eureka2.client.channel;

import com.netflix.eureka2.channel.RegistrationChannel;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.client.transport.TransportClients;
import com.netflix.eureka2.metric.client.EurekaClientMetricFactory;
import com.netflix.eureka2.transport.EurekaTransports;
import com.netflix.eureka2.transport.TransportClient;

/**
 * @author David Liu
 */
public class RegistrationChannelFactory extends ClientChannelFactory<RegistrationChannel> {

    private final TransportClient transport;

    public RegistrationChannelFactory(ServerResolver resolver,
                                      EurekaTransports.Codec codec,
                                      EurekaClientMetricFactory metricFactory) {
        this(TransportClients.newTcpRegistrationClient(resolver, codec), metricFactory);
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
