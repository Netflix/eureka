package com.netflix.rx.eureka.server;

import com.google.inject.AbstractModule;
import com.netflix.rx.eureka.registry.EurekaRegistry;
import com.netflix.rx.eureka.server.replication.ReplicationService;
import com.netflix.rx.eureka.server.service.BridgeService;
import com.netflix.rx.eureka.server.service.SelfRegistrationService;
import com.netflix.rx.eureka.server.service.WriteSelfRegistrationService;
import com.netflix.rx.eureka.server.spi.ExtensionContext;
import com.netflix.rx.eureka.server.transport.tcp.discovery.TcpDiscoveryServer;
import com.netflix.rx.eureka.server.transport.tcp.replication.TcpReplicationServer;
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
            bind(EurekaBootstrapConfig.class).to(BridgeServerConfig.class).asEagerSingleton();
        } else {
            bind(EurekaBootstrapConfig.class).toInstance(config);
            bind(BridgeServerConfig.class).toInstance(config);
        }
        bind(SelfRegistrationService.class).to(WriteSelfRegistrationService.class).asEagerSingleton();

        bind(EurekaRegistry.class).to(EurekaBridgeRegistry.class);

        bind(MetricEventsListenerFactory.class).toInstance(new ServoEventsListenerFactory());
        bind(TcpDiscoveryServer.class).asEagerSingleton();
        bind(TcpReplicationServer.class).asEagerSingleton();

        bind(ReplicationService.class).asEagerSingleton();
        bind(BridgeService.class).asEagerSingleton();

        bind(ExtensionContext.class).asEagerSingleton();
    }
}
