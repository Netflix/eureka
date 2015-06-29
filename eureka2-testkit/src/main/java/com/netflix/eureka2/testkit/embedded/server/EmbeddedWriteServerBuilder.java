package com.netflix.eureka2.testkit.embedded.server;

import java.util.ArrayList;
import java.util.List;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.util.Modules;
import com.netflix.eureka2.Server;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.server.AbstractEurekaServer;
import com.netflix.eureka2.server.EurekaWriteServerConfigurationModules;
import com.netflix.eureka2.server.EurekaWriteServerModule;
import com.netflix.eureka2.server.ReplicationPeerAddressesProvider;
import com.netflix.eureka2.server.config.WriteServerConfig;
import com.netflix.eureka2.server.module.CommonEurekaServerModule;
import com.netflix.eureka2.server.module.EurekaExtensionModule;
import com.netflix.eureka2.server.service.overrides.OverridesModule;
import com.netflix.eureka2.server.spi.ExtAbstractModule.ServerType;
import com.netflix.eureka2.server.transport.tcp.interest.TcpInterestServer;
import com.netflix.eureka2.server.transport.tcp.registration.TcpRegistrationServer;
import com.netflix.eureka2.server.transport.tcp.replication.TcpReplicationServer;
import com.netflix.eureka2.testkit.embedded.server.EmbeddedWriteServer.WriteServerReport;
import com.netflix.eureka2.testkit.netrouter.NetworkRouter;
import com.netflix.governator.Governator;
import com.netflix.governator.LifecycleInjector;
import rx.Observable;

/**
 * @author Tomasz Bak
 */
public class EmbeddedWriteServerBuilder extends EmbeddedServerBuilder<WriteServerConfig, WriteServerReport> {

    private WriteServerConfig configuration;
    private Observable<ChangeNotification<Server>> replicationPeers;
    private NetworkRouter networkRouter;
    private boolean adminUI;

    public EmbeddedWriteServerBuilder withConfiguration(WriteServerConfig configuration) {
        this.configuration = configuration;
        return this;
    }

    public EmbeddedWriteServerBuilder withReplicationPeers(Observable<ChangeNotification<Server>> replicationPeers) {
        this.replicationPeers = replicationPeers;
        return this;
    }

    public EmbeddedWriteServerBuilder withNetworkRouter(NetworkRouter networkRouter) {
        this.networkRouter = networkRouter;
        return this;
    }

    public EmbeddedWriteServerBuilder withAdminUI(boolean adminUI) {
        this.adminUI = adminUI;
        return this;
    }

    public EmbeddedWriteServer build() {
        List<Module> coreModules = new ArrayList<>();

        if (configuration == null) {
            coreModules.add(EurekaWriteServerConfigurationModules.fromArchaius());
        } else {
            coreModules.add(EurekaWriteServerConfigurationModules.fromConfig(configuration));
        }
        coreModules.add(new CommonEurekaServerModule());
        coreModules.add(new OverridesModule());
        coreModules.add(new EurekaExtensionModule(ServerType.Write));
        coreModules.add(new EurekaWriteServerModule());
        if (adminUI) {
            coreModules.add(new EmbeddedKaryonAdminModule(configuration.getWebAdminPort()));
        }

        List<Module> overrides = new ArrayList<>();
        overrides.add(
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(ReplicationPeerAddressesProvider.class).toInstance(new ReplicationPeerAddressesProvider(replicationPeers));
                        bind(AbstractEurekaServer.class).to(EmbeddedWriteServer.class);
                    }
                }
        );
        if (networkRouter != null) {
            overrides.add(new NetworkRouterModule(networkRouter));
        }

        LifecycleInjector injector = Governator.createInjector(Modules.override(Modules.combine(coreModules)).with(overrides));
        return injector.getInstance(EmbeddedWriteServer.class);
    }

    static class NetworkRouterModule extends AbstractModule {

        private final NetworkRouter networkRouter;

        NetworkRouterModule(NetworkRouter networkRouter) {
            this.networkRouter = networkRouter;
        }

        @Override
        protected void configure() {
            bind(NetworkRouter.class).toInstance(networkRouter);
            bind(TcpRegistrationServer.class).to(EmbeddedTcpRegistrationServer.class).in(Scopes.SINGLETON);
            bind(TcpReplicationServer.class).to(EmbeddedTcpReplicationServer.class).in(Scopes.SINGLETON);
            bind(TcpInterestServer.class).to(EmbeddedTcpInterestServer.class).in(Scopes.SINGLETON);
        }
    }
}
