package com.netflix.eureka2.server;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import com.netflix.eureka2.client.metric.EurekaClientConnectionMetrics;
import com.netflix.eureka2.metric.MessageConnectionMetrics;
import com.netflix.eureka2.metric.SerializedTaskInvokerMetrics;
import com.netflix.eureka2.server.channel.ReplicationService;
import com.netflix.eureka2.server.config.BridgeServerConfig;
import com.netflix.eureka2.server.config.EurekaCommonConfig;
import com.netflix.eureka2.server.config.EurekaServerConfig;
import com.netflix.eureka2.server.config.WriteServerConfig;
import com.netflix.eureka2.server.metric.BridgeChannelMetrics;
import com.netflix.eureka2.server.metric.BridgeServerMetricFactory;
import com.netflix.eureka2.server.metric.InterestChannelMetrics;
import com.netflix.eureka2.server.metric.RegistrationChannelMetrics;
import com.netflix.eureka2.server.metric.ReplicationChannelMetrics;
import com.netflix.eureka2.server.registry.EurekaBridgeRegistry;
import com.netflix.eureka2.server.registry.EurekaServerRegistry;
import com.netflix.eureka2.server.registry.eviction.EvictionQueue;
import com.netflix.eureka2.server.registry.eviction.EvictionQueueImpl;
import com.netflix.eureka2.server.registry.eviction.EvictionStrategy;
import com.netflix.eureka2.server.registry.eviction.EvictionStrategyProvider;
import com.netflix.eureka2.server.service.BridgeSelfRegistrationService;
import com.netflix.eureka2.server.service.BridgeService;
import com.netflix.eureka2.server.service.SelfRegistrationService;
import com.netflix.eureka2.server.spi.ExtensionContext;
import com.netflix.eureka2.server.transport.tcp.discovery.TcpDiscoveryServer;
import com.netflix.eureka2.server.transport.tcp.replication.TcpReplicationServer;
import io.reactivex.netty.metrics.MetricEventsListenerFactory;
import io.reactivex.netty.servo.ServoEventsListenerFactory;

/**
 * @author David Liu
 */
public class EurekaBridgeServerModule extends AbstractModule {

    private final BridgeServerConfig config;

    public EurekaBridgeServerModule() {
        this(null);
    }

    public EurekaBridgeServerModule(BridgeServerConfig config) {
        this.config = config;
    }

    @Override
    public void configure() {
        if (config == null) {
            bind(BridgeServerConfig.class).asEagerSingleton();
        } else {
            bind(EurekaCommonConfig.class).toInstance(config);
            bind(EurekaServerConfig.class).toInstance(config);
            bind(WriteServerConfig.class).toInstance(config);
            bind(BridgeServerConfig.class).toInstance(config);
        }
        bind(SelfRegistrationService.class).to(BridgeSelfRegistrationService.class).asEagerSingleton();

        bind(SerializedTaskInvokerMetrics.class).toInstance(new SerializedTaskInvokerMetrics("registry"));

        bind(EurekaServerRegistry.class).to(EurekaBridgeRegistry.class);
        bind(EvictionQueue.class).to(EvictionQueueImpl.class).asEagerSingleton();
        bind(EvictionStrategy.class).toProvider(EvictionStrategyProvider.class);

        bind(MetricEventsListenerFactory.class).annotatedWith(Names.named("discovery")).toInstance(new ServoEventsListenerFactory("discovery-rx-client-", "discovery-rx-server-"));
        bind(MetricEventsListenerFactory.class).annotatedWith(Names.named("replication")).toInstance(new ServoEventsListenerFactory("replication-rx-client-", "replication-rx-server-"));
        bind(TcpDiscoveryServer.class).asEagerSingleton();
        bind(TcpReplicationServer.class).asEagerSingleton();

        bind(ReplicationService.class).asEagerSingleton();
        bind(BridgeService.class).asEagerSingleton();

        bind(ExtensionContext.class).asEagerSingleton();

        // Metrics
        bind(MessageConnectionMetrics.class).annotatedWith(Names.named("registration")).toInstance(new MessageConnectionMetrics("registration"));
        bind(MessageConnectionMetrics.class).annotatedWith(Names.named("replication")).toInstance(new MessageConnectionMetrics("replication"));
        bind(MessageConnectionMetrics.class).annotatedWith(Names.named("discovery")).toInstance(new MessageConnectionMetrics("discovery"));

        bind(MessageConnectionMetrics.class).annotatedWith(Names.named("clientRegistration")).toInstance(new MessageConnectionMetrics("clientRegistration"));
        bind(MessageConnectionMetrics.class).annotatedWith(Names.named("clientDiscovery")).toInstance(new MessageConnectionMetrics("clientDiscovery"));
        bind(MessageConnectionMetrics.class).annotatedWith(Names.named("clientReplication")).toInstance(new MessageConnectionMetrics("clientReplication"));

        bind(EurekaClientConnectionMetrics.class).annotatedWith(Names.named("registration")).toInstance(new EurekaClientConnectionMetrics("registration"));
        bind(EurekaClientConnectionMetrics.class).annotatedWith(Names.named("discovery")).toInstance(new EurekaClientConnectionMetrics("discovery"));
        bind(EurekaClientConnectionMetrics.class).annotatedWith(Names.named("replication")).toInstance(new EurekaClientConnectionMetrics("replication"));

        bind(BridgeChannelMetrics.class).annotatedWith(Names.named("bridge")).toInstance(new BridgeChannelMetrics());

        bind(RegistrationChannelMetrics.class).toInstance(new RegistrationChannelMetrics());
        bind(ReplicationChannelMetrics.class).toInstance(new ReplicationChannelMetrics());
        bind(InterestChannelMetrics.class).toInstance(new InterestChannelMetrics());

        bind(BridgeServerMetricFactory.class).asEagerSingleton();
    }
}
