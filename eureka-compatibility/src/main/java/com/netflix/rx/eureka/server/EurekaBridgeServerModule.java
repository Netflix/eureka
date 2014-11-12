package com.netflix.rx.eureka.server;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import com.netflix.rx.eureka.client.transport.EurekaClientConnectionMetrics;
import com.netflix.rx.eureka.server.registry.EurekaServerRegistry;
import com.netflix.rx.eureka.server.registry.EurekaServerRegistryMetrics;
import com.netflix.rx.eureka.server.registry.EvictionQueue;
import com.netflix.rx.eureka.server.registry.EvictionQueueImpl;
import com.netflix.rx.eureka.server.registry.EvictionStrategies;
import com.netflix.rx.eureka.server.registry.EvictionStrategy;
import com.netflix.rx.eureka.server.replication.ReplicationService;
import com.netflix.rx.eureka.server.service.BridgeSelfRegistrationService;
import com.netflix.rx.eureka.server.service.BridgeService;
import com.netflix.rx.eureka.server.service.InterestChannelMetrics;
import com.netflix.rx.eureka.server.service.RegistrationChannelMetrics;
import com.netflix.rx.eureka.server.service.ReplicationChannelMetrics;
import com.netflix.rx.eureka.server.service.SelfRegistrationService;
import com.netflix.rx.eureka.server.spi.ExtensionContext;
import com.netflix.rx.eureka.server.transport.tcp.discovery.TcpDiscoveryServer;
import com.netflix.rx.eureka.server.transport.tcp.replication.TcpReplicationServer;
import com.netflix.rx.eureka.transport.base.MessageConnectionMetrics;
import io.reactivex.netty.metrics.MetricEventsListenerFactory;
import io.reactivex.netty.servo.ServoEventsListenerFactory;

/**
 * @author David Liu
 */
public class EurekaBridgeServerModule extends AbstractModule {

    // TODO: this should be configurable property
    private static final int ALLOWED_DROP = 20;
    private static final long EVICTION_TIMEOUT = 3 * 30000;

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
            bind(EurekaBootstrapConfig.class).to(BridgeServerConfig.class).asEagerSingleton();
        } else {
            bind(EurekaBootstrapConfig.class).toInstance(config);
            bind(BridgeServerConfig.class).toInstance(config);
        }
        bind(SelfRegistrationService.class).to(BridgeSelfRegistrationService.class).asEagerSingleton();

        bind(EurekaServerRegistry.class).to(EurekaBridgeRegistry.class);
        bind(EvictionQueue.class).toInstance(new EvictionQueueImpl(EVICTION_TIMEOUT));
        bind(EvictionStrategy.class).toInstance(EvictionStrategies.percentageDrop(ALLOWED_DROP));

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

        bind(RegistrationChannelMetrics.class).toInstance(new RegistrationChannelMetrics());
        bind(ReplicationChannelMetrics.class).toInstance(new ReplicationChannelMetrics());
        bind(InterestChannelMetrics.class).toInstance(new InterestChannelMetrics());

        bind(EurekaServerRegistryMetrics.class).toInstance(new EurekaServerRegistryMetrics("bridgeServer"));

        bind(ExtensionContext.class).asEagerSingleton();
    }
}
