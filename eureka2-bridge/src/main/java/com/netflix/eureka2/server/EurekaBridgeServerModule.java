package com.netflix.eureka2.server;

import com.google.inject.Scopes;
import com.netflix.eureka2.metric.server.BridgeServerMetricFactory;
import com.netflix.eureka2.metric.server.SpectatorBridgeServerMetricFactory;
import com.netflix.eureka2.metric.server.SpectatorWriteServerMetricFactory;
import com.netflix.eureka2.metric.server.WriteServerMetricFactory;
import com.netflix.eureka2.server.service.BridgeService;
import com.netflix.eureka2.server.service.EurekaBridgeServerSelfInfoResolver;
import com.netflix.eureka2.server.service.EurekaBridgeServerSelfRegistrationService;
import com.netflix.eureka2.server.service.SelfRegistrationService;
import com.netflix.eureka2.server.service.selfinfo.SelfInfoResolver;
import com.netflix.eureka2.server.spi.ExtAbstractModule.ServerType;
import com.netflix.eureka2.server.spi.ExtensionContext;

/**
 * @author David Liu
 */
public class EurekaBridgeServerModule extends EurekaWriteServerModule {

    @Override
    public void configure() {
        bindBase();
        bindMetricFactories();
        bindSelfInfo();

        bindInterestComponents();
        bindReplicationComponents();

        bindRegistryComponents();

        // bridge server specific stuff
        bind(BridgeService.class).asEagerSingleton();
        bind(ExtensionContext.class).asEagerSingleton();
        bind(ServerType.class).toInstance(ServerType.Bridge);
        bind(AbstractEurekaServer.class).to(EurekaBridgeServer.class);
        bind(BridgeServerMetricFactory.class).to(SpectatorBridgeServerMetricFactory.class).asEagerSingleton();
        bind(WriteServerMetricFactory.class).to(SpectatorWriteServerMetricFactory.class).asEagerSingleton();
    }

    @Override
    protected void bindSelfInfo() {
        bind(SelfInfoResolver.class).to(EurekaBridgeServerSelfInfoResolver.class);
        bind(SelfRegistrationService.class).to(EurekaBridgeServerSelfRegistrationService.class);
    }
}
