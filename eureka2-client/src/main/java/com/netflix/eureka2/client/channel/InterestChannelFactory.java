package com.netflix.eureka2.client.channel;

import com.netflix.eureka2.channel.InterestChannel;
import com.netflix.eureka2.client.interest.BatchingRegistry;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.client.transport.TransportClients;
import com.netflix.eureka2.config.EurekaTransportConfig;
import com.netflix.eureka2.metric.client.EurekaClientMetricFactory;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.transport.TransportClient;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @author David Liu
 */
public class InterestChannelFactory extends ClientChannelFactory<InterestChannel> {

    private final SourcedEurekaRegistry<InstanceInfo> eurekaRegistry;
    private final TransportClient transport;
    private final BatchingRegistry<InstanceInfo> remoteBatchingRegistry;

    private final AtomicLong generationId;

    public InterestChannelFactory(String clientId,
                                  EurekaTransportConfig config,
                                  ServerResolver resolver,
                                  SourcedEurekaRegistry<InstanceInfo> eurekaRegistry,
                                  BatchingRegistry<InstanceInfo> remoteBatchingRegistry,
                                  EurekaClientMetricFactory metricFactory) {
        this(
                TransportClients.newTcpDiscoveryClient(clientId, config, resolver, metricFactory),
                eurekaRegistry, remoteBatchingRegistry, metricFactory
        );
    }

    public InterestChannelFactory(TransportClient transport,
                                  SourcedEurekaRegistry<InstanceInfo> eurekaRegistry,
                                  BatchingRegistry<InstanceInfo> remoteBatchingRegistry,
                                  EurekaClientMetricFactory metricFactory) {
        super(metricFactory);
        this.eurekaRegistry = eurekaRegistry;
        this.transport = transport;
        this.remoteBatchingRegistry = remoteBatchingRegistry;
        this.generationId = new AtomicLong(System.currentTimeMillis());  // seed with system time to avoid reset on reboot
    }

    @Override
    public InterestChannel newChannel() {
        InterestChannel baseChannel = new InterestChannelImpl(
                eurekaRegistry, remoteBatchingRegistry, transport,
                generationId.getAndIncrement(), metricFactory.getInterestChannelMetrics());
        return new InterestChannelInvoker(baseChannel, metricFactory);
    }

    @Override
    public void shutdown() {
        transport.shutdown();
    }
}
