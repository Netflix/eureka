package com.netflix.eureka2.client.channel;

import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.client.transport.TransportClients;
import com.netflix.eureka2.metric.client.EurekaClientMetricFactory;
import com.netflix.eureka2.registry.PreservableEurekaRegistry;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.transport.EurekaTransports;
import com.netflix.eureka2.transport.TransportClient;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

/**
 * @author David Liu
 */
public class InterestChannelFactory extends ClientChannelFactory<ClientInterestChannel> {

    private final PreservableEurekaRegistry eurekaRegistry;
    private final TransportClient transport;

    public InterestChannelFactory(ServerResolver resolver,
                                  EurekaTransports.Codec codec,
                                  PreservableEurekaRegistry eurekaRegistry,
                                  long retryInitialDelayMs,
                                  EurekaClientMetricFactory metricFactory) {
        this(TransportClients.newTcpDiscoveryClient(resolver, codec),
                eurekaRegistry,
                retryInitialDelayMs,
                metricFactory);
    }

    public InterestChannelFactory(TransportClient transport,
                                  PreservableEurekaRegistry eurekaRegistry,
                                  long retryInitialDelayMs,
                                  EurekaClientMetricFactory metricFactory) {
        super(retryInitialDelayMs, metricFactory);
        this.eurekaRegistry = eurekaRegistry;
        this.transport = transport;
    }


    @Override
    public ClientInterestChannel newChannel() {
        RetryableInterestChannel retryable = new RetryableInterestChannel(
                new Func1<SourcedEurekaRegistry<InstanceInfo>, ClientInterestChannel>() {
                    @Override
                    public ClientInterestChannel call(SourcedEurekaRegistry<InstanceInfo> registry) {
                        return new InterestChannelImpl(registry, transport, metricFactory.getInterestChannelMetrics());
                    }
                }, eurekaRegistry, retryInitialDelayMs, Schedulers.computation()
        );
        return new InterestChannelInvoker(retryable, metricFactory);
    }

    @Override
    public void shutdown() {
        transport.shutdown();
    }
}
