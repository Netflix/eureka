package com.netflix.eureka2.client.channel;

import com.netflix.eureka2.channel.RegistrationChannel;
import com.netflix.eureka2.client.metric.EurekaClientMetricFactory;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.client.transport.TransportClients;
import com.netflix.eureka2.transport.EurekaTransports;
import com.netflix.eureka2.transport.TransportClient;
import rx.functions.Func0;
import rx.schedulers.Schedulers;

/**
 * @author David Liu
 */
public class RegistrationChannelFactory extends ClientChannelFactory<RegistrationChannel> {

    private final TransportClient transport;

    public RegistrationChannelFactory(ServerResolver resolver,
                                      EurekaTransports.Codec codec,
                                      long retryInitialDelayMs,
                                      EurekaClientMetricFactory metricFactory) {
        this(TransportClients.newTcpRegistrationClient(resolver, codec), retryInitialDelayMs, metricFactory);
    }

    public RegistrationChannelFactory(TransportClient transport,
                                      long retryInitialDelayMs,
                                      EurekaClientMetricFactory metricFactory) {
        super(retryInitialDelayMs, metricFactory);
        this.transport = transport;
    }

    @Override
    public RegistrationChannel newChannel() {
        return new RegistrationChannelInvoker(
                new RetryableRegistrationChannel(new Func0<RegistrationChannel>() {
                    @Override
                    public RegistrationChannel call() {
                        return new RegistrationChannelImpl(transport, metricFactory.getRegistrationChannelMetrics());
                    }
                }, retryInitialDelayMs, Schedulers.computation())
        );
    }

    @Override
    public void shutdown() {
        transport.shutdown();
    }
}
