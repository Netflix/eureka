package com.netflix.eureka2.server;

import com.google.inject.name.Names;
import com.netflix.eureka2.config.EurekaRegistryConfig;
import com.netflix.eureka2.metric.EurekaRegistryMetricFactory;
import com.netflix.eureka2.metric.SpectatorEurekaRegistryMetricFactory;
import com.netflix.eureka2.metric.server.BridgeServerMetricFactory;
import com.netflix.eureka2.metric.server.EurekaServerMetricFactory;
import com.netflix.eureka2.metric.server.SpectatorBridgeServerMetricFactory;
import com.netflix.eureka2.metric.server.SpectatorWriteServerMetricFactory;
import com.netflix.eureka2.metric.server.WriteServerMetricFactory;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.eviction.EvictionQueue;
import com.netflix.eureka2.registry.eviction.EvictionQueueImpl;
import com.netflix.eureka2.registry.eviction.EvictionStrategy;
import com.netflix.eureka2.registry.eviction.EvictionStrategyProvider;
import com.netflix.eureka2.server.config.BridgeServerConfig;
import com.netflix.eureka2.server.config.EurekaCommonConfig;
import com.netflix.eureka2.server.config.EurekaServerConfig;
import com.netflix.eureka2.server.config.WriteServerConfig;
import com.netflix.eureka2.server.registry.EurekaBridgeRegistry;
import com.netflix.eureka2.server.service.BridgeService;
import com.netflix.eureka2.server.service.EurekaBridgeServerSelfInfoResolver;
import com.netflix.eureka2.server.service.EurekaBridgeServerSelfRegistrationService;
import com.netflix.eureka2.server.service.SelfInfoResolver;
import com.netflix.eureka2.server.service.SelfRegistrationService;
import com.netflix.eureka2.server.service.replication.ReplicationService;
import com.netflix.eureka2.server.spi.ExtensionContext;
import com.netflix.eureka2.server.transport.tcp.discovery.TcpDiscoveryServer;
import com.netflix.eureka2.server.transport.tcp.replication.TcpReplicationServer;
import io.reactivex.netty.metrics.MetricEventsListenerFactory;
import io.reactivex.netty.spectator.SpectatorEventsListenerFactory;

/**
 * @author David Liu
 */
public class EurekaBridgeServerModule extends AbstractEurekaServerModule {

    private final BridgeServerConfig config;

    public EurekaBridgeServerModule() {
        this(null);
    }

    public EurekaBridgeServerModule(BridgeServerConfig config) {
        this.config = config;
    }

    @Override
    public void configureEureka() {
        if (config == null) {
            bind(BridgeServerConfig.class).asEagerSingleton();
            bind(EurekaCommonConfig.class).to(BridgeServerConfig.class);
            bind(EurekaRegistryConfig.class).to(BridgeServerConfig.class);
        } else {
            bind(EurekaRegistryConfig.class).toInstance(config);
            bind(EurekaCommonConfig.class).toInstance(config);
            bind(EurekaServerConfig.class).toInstance(config);
            bind(WriteServerConfig.class).toInstance(config);
            bind(BridgeServerConfig.class).toInstance(config);
        }

        bind(SourcedEurekaRegistry.class).to(EurekaBridgeRegistry.class);
        bind(EvictionQueue.class).to(EvictionQueueImpl.class).asEagerSingleton();
        bind(EvictionStrategy.class).toProvider(EvictionStrategyProvider.class);

        bind(SelfInfoResolver.class).to(EurekaBridgeServerSelfInfoResolver.class).asEagerSingleton();
        bind(SelfRegistrationService.class).to(EurekaBridgeServerSelfRegistrationService.class).asEagerSingleton();

        bind(MetricEventsListenerFactory.class).annotatedWith(Names.named("discovery")).toInstance(new SpectatorEventsListenerFactory("discovery-rx-client-", "discovery-rx-server-"));
        bind(MetricEventsListenerFactory.class).annotatedWith(Names.named("replication")).toInstance(new SpectatorEventsListenerFactory("replication-rx-client-", "replication-rx-server-"));
        bind(TcpDiscoveryServer.class).asEagerSingleton();
        bind(TcpReplicationServer.class).asEagerSingleton();

        bind(ReplicationService.class).asEagerSingleton();
        bind(BridgeService.class).asEagerSingleton();

        bind(ExtensionContext.class).asEagerSingleton();

        // Metrics
        bind(EurekaRegistryMetricFactory.class).to(SpectatorEurekaRegistryMetricFactory.class).asEagerSingleton();

        bind(EurekaServerMetricFactory.class).to(SpectatorWriteServerMetricFactory.class).asEagerSingleton();
        bind(WriteServerMetricFactory.class).to(SpectatorWriteServerMetricFactory.class).asEagerSingleton();
        bind(BridgeServerMetricFactory.class).to(SpectatorBridgeServerMetricFactory.class).asEagerSingleton();
    }
}
