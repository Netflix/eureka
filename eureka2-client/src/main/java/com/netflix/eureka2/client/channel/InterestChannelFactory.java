package com.netflix.eureka2.client.channel;

import com.netflix.eureka2.channel.InterestChannel;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.client.transport.TransportClients;
import com.netflix.eureka2.config.EurekaTransportConfig;
import com.netflix.eureka2.metric.client.EurekaClientMetricFactory;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.registry.EurekaRegistry;
import com.netflix.eureka2.transport.TransportClient;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @author David Liu
 */
public class InterestChannelFactory extends ClientChannelFactory<InterestChannel> {

    private final EurekaRegistry<InstanceInfo> eurekaRegistry;
    private final TransportClient transport;

    private final AtomicLong generationId;

    public InterestChannelFactory(String clientId,
                                  EurekaTransportConfig config,
                                  ServerResolver resolver,
                                  EurekaRegistry<InstanceInfo> eurekaRegistry,
                                  EurekaClientMetricFactory metricFactory) {
        this(
                TransportClients.newTcpInterestClient(clientId, config, resolver, metricFactory),
                eurekaRegistry,
                metricFactory
        );
    }

    public InterestChannelFactory(TransportClient transport,
                                  EurekaRegistry<InstanceInfo> eurekaRegistry,
                                  EurekaClientMetricFactory metricFactory) {
        super(metricFactory);
        this.eurekaRegistry = eurekaRegistry;
        this.transport = transport;
        this.generationId = new AtomicLong(System.currentTimeMillis());  // seed with system time to avoid reset on reboot
    }

    @Override
    public InterestChannel newChannel() {
        InterestChannel baseChannel = new ClientInterestChannel(
                eurekaRegistry,
                transport,
                generationId.getAndIncrement(),
                metricFactory.getInterestChannelMetrics()
        );
        return new InterestChannelInvoker(baseChannel, metricFactory);
    }

    @Override
    public void shutdown() {
        transport.shutdown();
    }
}
