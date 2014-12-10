package com.netflix.eureka2.server.channel;

import com.netflix.eureka2.registry.InstanceInfo;
import com.netflix.eureka2.server.metric.WriteServerMetricFactory;
import com.netflix.eureka2.server.registry.EurekaServerRegistry;
import com.netflix.eureka2.server.registry.eviction.EvictionQueue;
import com.netflix.eureka2.channel.RegistrationChannel;
import com.netflix.eureka2.server.service.WriteSelfRegistrationService;
import com.netflix.eureka2.transport.MessageConnection;

/**
 * An implementation of {@link ServerChannelFactory} associated with strictly one {@link MessageConnection}
 *
 * <h2>Thread safety</h2>
 *
 * See {@link ServerChannelFactory} for details. This service assumes sequential (single threaded) invocations.
 *
 * @author Nitesh Kant
 */
public class ServerChannelFactoryImpl extends InterestChannelFactoryImpl implements ServerChannelFactory {

    private final WriteSelfRegistrationService selfRegistrationService;
    private final EvictionQueue evictionQueue;
    private final WriteServerMetricFactory metricFactory;

    public ServerChannelFactoryImpl(EurekaServerRegistry<InstanceInfo> registry,
                                    WriteSelfRegistrationService selfRegistrationService,
                                    EvictionQueue evictionQueue,
                                    MessageConnection connection,
                                    WriteServerMetricFactory metricFactory) {
        super(registry, connection, metricFactory);
        this.selfRegistrationService = selfRegistrationService;
        this.evictionQueue = evictionQueue;
        this.metricFactory = metricFactory;
    }

    @Override
    public RegistrationChannel newRegistrationChannel() {
        return new RegistrationChannelImpl(registry, evictionQueue, connection, metricFactory.getRegistrationChannelMetrics());
    }

    @Override
    public ReplicationChannel newReplicationChannel() {
        return new ReceiverReplicationChannel(connection, selfRegistrationService, registry, evictionQueue, metricFactory.getReplicationChannelMetrics());
    }

    @Override
    public void shutdown() {
        connection.shutdown();
    }
}
