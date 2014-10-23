package com.netflix.rx.eureka.client.service;

import com.netflix.rx.eureka.client.metric.EurekaClientMetricFactory;
import com.netflix.rx.eureka.client.transport.TransportClient;
import com.netflix.rx.eureka.registry.EurekaRegistry;
import com.netflix.rx.eureka.registry.InstanceInfo;
import com.netflix.rx.eureka.service.EurekaService;
import com.netflix.rx.eureka.service.InterestChannel;
import com.netflix.rx.eureka.service.RegistrationChannel;

/**
 * @author Nitesh Kant
 */
public class EurekaServiceImpl implements EurekaService {

    private final EurekaRegistry<InstanceInfo> registry; /*Null for write server only service*/
    private final TransportClient readServerClient; /*Null for write server only service*/
    private final TransportClient writeServerClient; /*Null for read server only service*/
    private final EurekaClientMetricFactory metricFactory;

    protected EurekaServiceImpl(EurekaRegistry<InstanceInfo> registry, TransportClient writeServerClient,
                                TransportClient readServerClient, EurekaClientMetricFactory metricFactory) {
        this.registry = registry;
        this.writeServerClient = writeServerClient;
        this.readServerClient = readServerClient;
        this.metricFactory = metricFactory;
    }

    public static EurekaService forReadServer(EurekaRegistry<InstanceInfo> registry, TransportClient client, EurekaClientMetricFactory metricFactory) {
        return new EurekaServiceImpl(registry, null, client, metricFactory);
    }

    public static EurekaService forWriteServer(TransportClient client, EurekaClientMetricFactory metricFactory) {
        return new EurekaServiceImpl(null, client, null, metricFactory);
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
    public InterestChannel newInterestChannel() {
        return new InterestChannelInvoker(new InterestChannelImpl(registry, readServerClient, metricFactory.getInterestChannelMetrics()));
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
        return new RegistrationChannelInvoker(new RegistrationChannelImpl(writeServerClient, metricFactory.getRegistrationChannelMetrics()));
    }

    @Override
    public void shutdown() {
        if (null != readServerClient) {
            readServerClient.shutdown();
        }
        if (null != writeServerClient) {
            writeServerClient.shutdown();
        }
        if (null != registry) {
            registry.shutdown();
        }
    }

    // for debugging
    @Override
    public String toString() {
        return registry.toString();
    }
}
