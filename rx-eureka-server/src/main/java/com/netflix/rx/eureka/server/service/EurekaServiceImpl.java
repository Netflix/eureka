package com.netflix.rx.eureka.server.service;

import com.netflix.rx.eureka.registry.InstanceInfo;
import com.netflix.rx.eureka.server.metric.EurekaServerMetricFactory;
import com.netflix.rx.eureka.server.registry.EurekaServerRegistry;
import com.netflix.rx.eureka.server.registry.EvictionQueue;
import com.netflix.rx.eureka.service.InterestChannel;
import com.netflix.rx.eureka.service.RegistrationChannel;
import com.netflix.rx.eureka.transport.MessageConnection;

/**
 * An implementation of {@link EurekaServerService} associated with strictly one {@link MessageConnection}
 *
 * <h2>Thread safety</h2>
 *
 * See {@link EurekaServerService} for details. This service assumes sequential (single threaded) invocations.
 *
 * @author Nitesh Kant
 */
public class EurekaServiceImpl implements EurekaServerService {

    private final EurekaServerRegistry<InstanceInfo> registry;
    private final EvictionQueue evictionQueue;
    private final MessageConnection connection;
    private final EurekaServerMetricFactory metricFactory;

    public EurekaServiceImpl(EurekaServerRegistry<InstanceInfo> registry,
                             EvictionQueue evictionQueue,
                             MessageConnection connection,
                             EurekaServerMetricFactory metricFactory) {
        this.registry = registry;
        this.evictionQueue = evictionQueue;
        this.connection = connection;
        this.metricFactory = metricFactory;
    }

    @Override
    public InterestChannel newInterestChannel() {
        return new InterestChannelImpl(registry, connection, metricFactory.getInterestChannelMetrics());
    }

    @Override
    public RegistrationChannel newRegistrationChannel() {
        return new RegistrationChannelImpl(registry, evictionQueue, connection, metricFactory.getRegistrationChannelMetrics());
    }

    @Override
    public ReplicationChannel newReplicationChannel() {
        return new ReplicationChannelImpl(connection, registry, evictionQueue, metricFactory.getReplicationChannelMetrics());
    }

    @Override
    public void shutdown() {
        connection.shutdown();
    }
}
