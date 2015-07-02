package com.netflix.eureka2.server;

import javax.inject.Singleton;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.name.Names;
import com.netflix.archaius.ConfigProxyFactory;
import com.netflix.eureka2.config.EurekaRegistryConfig;
import com.netflix.eureka2.interests.IndexRegistry;
import com.netflix.eureka2.interests.IndexRegistryImpl;
import com.netflix.eureka2.metric.EurekaRegistryMetricFactory;
import com.netflix.eureka2.metric.SpectatorEurekaRegistryMetricFactory;
import com.netflix.eureka2.metric.server.BridgeServerMetricFactory;
import com.netflix.eureka2.metric.server.EurekaServerMetricFactory;
import com.netflix.eureka2.metric.server.SpectatorBridgeServerMetricFactory;
import com.netflix.eureka2.metric.server.SpectatorWriteServerMetricFactory;
import com.netflix.eureka2.metric.server.WriteServerMetricFactory;
import com.netflix.eureka2.registry.EurekaRegistryView;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.SourcedEurekaRegistryImpl;
import com.netflix.eureka2.registry.eviction.EvictionQueue;
import com.netflix.eureka2.registry.eviction.EvictionQueueImpl;
import com.netflix.eureka2.registry.eviction.EvictionStrategy;
import com.netflix.eureka2.registry.eviction.EvictionStrategyProvider;
import com.netflix.eureka2.server.config.BootstrapConfig;
import com.netflix.eureka2.server.config.BridgeServerConfig;
import com.netflix.eureka2.server.config.EurekaClusterDiscoveryConfig;
import com.netflix.eureka2.server.config.EurekaServerConfig;
import com.netflix.eureka2.server.config.EurekaServerTransportConfig;
import com.netflix.eureka2.server.config.WriteServerConfig;
import com.netflix.eureka2.server.registry.EurekaBridgeRegistry;
import com.netflix.eureka2.server.service.BridgeService;
import com.netflix.eureka2.server.service.EurekaBridgeServerSelfInfoResolver;
import com.netflix.eureka2.server.service.EurekaBridgeServerSelfRegistrationService;
import com.netflix.eureka2.server.service.selfinfo.SelfInfoResolver;
import com.netflix.eureka2.server.service.SelfRegistrationService;
import com.netflix.eureka2.server.service.replication.ReplicationService;
import com.netflix.eureka2.server.spi.ExtensionContext;
import com.netflix.eureka2.server.transport.tcp.interest.TcpInterestServer;
import com.netflix.eureka2.server.transport.tcp.replication.TcpReplicationServer;
import io.reactivex.netty.metrics.MetricEventsListenerFactory;
import io.reactivex.netty.spectator.SpectatorEventsListenerFactory;

/**
 * @author David Liu
 */
public class EurekaBridgeServerModule extends AbstractModule {

    private final String prefix;

    public EurekaBridgeServerModule(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public void configure() {
        bind(IndexRegistry.class).to(IndexRegistryImpl.class).asEagerSingleton();

        bind(SourcedEurekaRegistry.class).annotatedWith(Names.named("delegate")).to(EurekaBridgeRegistry.class).asEagerSingleton();
        bind(SourcedEurekaRegistry.class).to(SourcedEurekaRegistryImpl.class);
        bind(EurekaRegistryView.class).to(SourcedEurekaRegistryImpl.class);

        bind(EvictionQueue.class).to(EvictionQueueImpl.class).asEagerSingleton();
        bind(EvictionStrategy.class).toProvider(EvictionStrategyProvider.class);

        bind(SelfInfoResolver.class).to(EurekaBridgeServerSelfInfoResolver.class).asEagerSingleton();
        bind(SelfRegistrationService.class).to(EurekaBridgeServerSelfRegistrationService.class).asEagerSingleton();

        bind(MetricEventsListenerFactory.class).annotatedWith(Names.named(com.netflix.eureka2.Names.INTEREST)).toInstance(new SpectatorEventsListenerFactory("discovery-rx-client-", "discovery-rx-server-"));
        bind(MetricEventsListenerFactory.class).annotatedWith(Names.named(com.netflix.eureka2.Names.REPLICATION)).toInstance(new SpectatorEventsListenerFactory("replication-rx-client-", "replication-rx-server-"));
        bind(TcpInterestServer.class).asEagerSingleton();
        bind(TcpReplicationServer.class).asEagerSingleton();

        bind(ReplicationService.class).asEagerSingleton();
        bind(BridgeService.class).asEagerSingleton();

        bind(ExtensionContext.class).asEagerSingleton();

        // Metrics
        bind(EurekaRegistryMetricFactory.class).to(SpectatorEurekaRegistryMetricFactory.class).asEagerSingleton();

        bind(EurekaServerMetricFactory.class).to(SpectatorWriteServerMetricFactory.class).asEagerSingleton();
        bind(WriteServerMetricFactory.class).to(SpectatorWriteServerMetricFactory.class).asEagerSingleton();
        bind(BridgeServerMetricFactory.class).to(SpectatorBridgeServerMetricFactory.class).asEagerSingleton();

        bind(AbstractEurekaServer.class).to(EurekaBridgeServer.class);
    }

    @Provides
    @Singleton
    public BridgeServerConfig getConfiguration(ConfigProxyFactory factory) {
        return factory.newProxy(BridgeServerConfig.class, prefix);
    }

    @Provides
    @Singleton
    public EurekaServerConfig getEurekaServerConfig(BridgeServerConfig rootConfig) {
        return rootConfig;
    }

    @Provides
    @Singleton
    public WriteServerConfig getWriteServerConfig(BridgeServerConfig rootConfig) {
        return rootConfig;
    }

    @Provides
    @Singleton
    public EurekaServerTransportConfig getEurekaServerTransportConfig(BridgeServerConfig rootConfig) {
        return rootConfig.getEurekaTransport();
    }

    @Provides
    @Singleton
    public EurekaRegistryConfig getEurekaRegistryConfig(BridgeServerConfig rootConfig) {
        return rootConfig.getEurekaRegistry();
    }

    @Provides
    @Singleton
    public EurekaClusterDiscoveryConfig getEurekaClusterDiscoveryConfig(BridgeServerConfig rootConfig) {
        return rootConfig.getEurekaClusterDiscovery();
    }

    @Provides
    @Singleton
    public BootstrapConfig getBootstrapConfig(BridgeServerConfig rootConfig) {
        return rootConfig.getBootstrap();
    }
}
