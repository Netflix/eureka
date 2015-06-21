package com.netflix.eureka2.server.transport.tcp.replication;

import com.netflix.eureka2.metric.server.WriteServerMetricFactory;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.server.channel.ReceiverReplicationChannel;
import com.netflix.eureka2.server.config.WriteServerConfig;
import com.netflix.eureka2.server.service.SelfInfoResolver;
import com.netflix.eureka2.transport.MessageConnection;

/**
 * For integ testing to expose the doHandle method
 *
 * @author David Liu
 */
public class TestTcpReplicationHandler extends TcpReplicationHandler {

    public TestTcpReplicationHandler(
            WriteServerConfig config,
            SelfInfoResolver selfIdentityService,
            SourcedEurekaRegistry registry,
            WriteServerMetricFactory metricFactory
    ) {
        super(config, selfIdentityService, registry, metricFactory);
    }

    @Override
    public ReceiverReplicationChannel doHandle(MessageConnection connection) {
        return super.doHandle(connection);
    }
}
