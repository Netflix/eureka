package com.netflix.eureka2.client.channel;

import com.netflix.eureka2.channel.InterestChannel;
import com.netflix.eureka2.channel.RegistrationChannel;
import com.netflix.eureka2.client.metric.EurekaClientMetricFactory;
import com.netflix.eureka2.registry.PreservableEurekaRegistry;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.transport.TransportClient;
import rx.functions.Func0;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

/**
 * @author Nitesh Kant
 */
public class ClientChannelFactoryImpl implements ClientChannelFactory {

    private final TransportClient readServerClient; /*Null for write server only service*/
    private final TransportClient writeServerClient; /*Null for read server only service*/
    private final PreservableEurekaRegistry eurekaRegistry;
    private final EurekaClientMetricFactory metricFactory;
    private final Mode channelMode;
    private final long retryInitialDelayMs;

    public ClientChannelFactoryImpl(TransportClient writeServerClient,
                                    TransportClient readServerClient,
                                    PreservableEurekaRegistry eurekaRegistry,
                                    long retryInitialDelayMs,
                                    EurekaClientMetricFactory metricFactory) {
        this.retryInitialDelayMs = retryInitialDelayMs;
        if (writeServerClient == null && readServerClient == null) {
            throw new IllegalArgumentException("Both read and write transport clients are null");
        }
        this.writeServerClient = writeServerClient;
        this.readServerClient = readServerClient;
        this.eurekaRegistry = eurekaRegistry;
        this.metricFactory = metricFactory;
        this.channelMode = writeServerClient == null ? Mode.Read : readServerClient == null ? Mode.Write : Mode.ReadWrite;
    }

    /**
     * Returns an {@link InterestChannel} which is not yet connected to any eureka servers. The connection is done
     * lazily when any operation is invoked on the channel.
     *
     * This makes it possible for clients to create this channel eagerly and use it when required.
     *
     * @return An {@link InterestChannel} which is not yet connected to any eureka servers.
     */
    @Override
    public ClientInterestChannel newInterestChannel() {
        return new InterestChannelInvoker(
                new RetryableInterestChannel(new Func1<SourcedEurekaRegistry<InstanceInfo>, ClientInterestChannel>() {
                    @Override
                    public ClientInterestChannel call(SourcedEurekaRegistry<InstanceInfo> registry) {
                        return new InterestChannelImpl(registry, readServerClient, metricFactory.getInterestChannelMetrics());
                    }
                }, eurekaRegistry, retryInitialDelayMs, Schedulers.computation())
        );
    }


    /**
     * Returns an {@link RegistrationChannel} which is not yet connected to any eureka servers. The connection is done
     * lazily when any operation is invoked on the channel.
     *
     * This makes it possible for clients to create this channel eagerly and use it when required.
     *
     * @return An {@link RegistrationChannel} which is not yet connected to any eureka servers.
     */
    @Override
    public RegistrationChannel newRegistrationChannel() {
        return new RegistrationChannelInvoker(
                new RetryableRegistrationChannel(new Func0<RegistrationChannel>() {
                    @Override
                    public RegistrationChannel call() {
                        return new RegistrationChannelImpl(writeServerClient, metricFactory.getRegistrationChannelMetrics());
                    }
                }, retryInitialDelayMs, Schedulers.computation())
        );
    }

    @Override
    public void shutdown() {
        if (null != readServerClient) {
            readServerClient.shutdown();
        }
        if (null != writeServerClient) {
            writeServerClient.shutdown();
        }
    }
}
