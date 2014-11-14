package com.netflix.eureka2.server.service;

import com.netflix.eureka2.registry.InstanceInfo;
import com.netflix.eureka2.server.metric.EurekaServerMetricFactory;
import com.netflix.eureka2.server.registry.EurekaServerRegistry;
import com.netflix.eureka2.server.registry.eviction.EvictionQueue;
import com.netflix.eureka2.service.InterestChannel;
import com.netflix.eureka2.service.RegistrationChannel;
import com.netflix.eureka2.transport.MessageConnection;

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
