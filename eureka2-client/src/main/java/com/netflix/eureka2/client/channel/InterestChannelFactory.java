package com.netflix.eureka2.client.channel;

import com.netflix.eureka2.channel.InterestChannel;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.client.transport.TransportClients;
import com.netflix.eureka2.config.EurekaTransportConfig;
import com.netflix.eureka2.metric.client.EurekaClientMetricFactory;
import com.netflix.eureka2.registry.PreservableEurekaRegistry;
import com.netflix.eureka2.transport.TransportClient;

/**
 * @author David Liu
 */
public class InterestChannelFactory extends ClientChannelFactory<InterestChannel> {

    private final PreservableEurekaRegistry eurekaRegistry;
    private final TransportClient transport;

    public InterestChannelFactory(EurekaTransportConfig config,
                                  ServerResolver resolver,
                                  PreservableEurekaRegistry eurekaRegistry,
                                  EurekaClientMetricFactory metricFactory) {
        this(TransportClients.newTcpDiscoveryClient(config, resolver, metricFactory), eurekaRegistry, metricFactory);
    }

    public InterestChannelFactory(TransportClient transport,
                                  PreservableEurekaRegistry eurekaRegistry,
                                  EurekaClientMetricFactory metricFactory) {
        super(metricFactory);
        this.eurekaRegistry = eurekaRegistry;
        this.transport = transport;
    }

    @Override
    public InterestChannel newChannel() {
        InterestChannel baseChannel = new InterestChannelImpl(eurekaRegistry, transport, metricFactory.getInterestChannelMetrics());
        return new InterestChannelInvoker(baseChannel, metricFactory);
    }

    @Override
    public void shutdown() {
        transport.shutdown();
    }
}
