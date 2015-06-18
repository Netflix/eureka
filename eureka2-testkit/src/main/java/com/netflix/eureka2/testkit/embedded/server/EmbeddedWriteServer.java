package com.netflix.eureka2.testkit.embedded.server;

import java.util.Properties;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.netflix.eureka2.Server;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.client.resolver.ServerResolvers;
import com.netflix.eureka2.eureka1.rest.Eureka1Configuration;
import com.netflix.eureka2.eureka1.rest.Eureka1RestApiModule;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.server.EurekaWriteServerModule;
import com.netflix.eureka2.server.InterestPeerAddressProvider;
import com.netflix.eureka2.server.ReplicationPeerAddressesProvider;
import com.netflix.eureka2.server.config.WriteServerConfig;
import com.netflix.eureka2.server.spi.ExtAbstractModule.ServerType;
import com.netflix.eureka2.server.transport.tcp.discovery.TcpDiscoveryServer;
import com.netflix.eureka2.server.transport.tcp.registration.TcpRegistrationServer;
import com.netflix.eureka2.server.transport.tcp.replication.TcpReplicationServer;
import com.netflix.eureka2.testkit.embedded.server.EmbeddedWriteServer.WriteServerReport;
import com.netflix.eureka2.testkit.netrouter.NetworkRouter;
import rx.Observable;

/**
 * @author Tomasz Bak
 */
public class EmbeddedWriteServer extends EmbeddedEurekaServer<WriteServerConfig, WriteServerReport> {

    private final Observable<ChangeNotification<Server>> interestPeers;
    private final Observable<ChangeNotification<Server>> replicationPeers;
    private final NetworkRouter networkRouter;

    public EmbeddedWriteServer(final WriteServerConfig config,
                               final Observable<ChangeNotification<Server>> interestPeers,
                               final Observable<ChangeNotification<Server>> replicationPeers,
                               NetworkRouter networkRouter,
                               boolean withExt,
                               boolean withDashboards) {
        super(ServerType.Write, config, withExt, withDashboards);
        this.interestPeers = interestPeers;
        this.replicationPeers = replicationPeers;
        this.networkRouter = networkRouter;
    }

    @Override
    public void start() {
        Module[] modules = {
                new EurekaWriteServerModule(config),
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(InterestPeerAddressProvider.class).toInstance(new InterestPeerAddressProvider(interestPeers));
                        bind(ReplicationPeerAddressesProvider.class).toInstance(new ReplicationPeerAddressesProvider(replicationPeers));
                        if (networkRouter != null) {
                            bind(NetworkRouter.class).toInstance(networkRouter);
                            bind(TcpRegistrationServer.class).to(EmbeddedTcpRegistrationServer.class);
                            bind(TcpDiscoveryServer.class).to(EmbeddedTcpDiscoveryServer.class);
                            bind(TcpReplicationServer.class).to(EmbeddedTcpReplicationServer.class);
                        }
                    }
                },
                new Eureka1RestApiModule(new Eureka1Configuration(), ServerType.Write)
        };

        setup(modules);
    }

    @Override
    protected void loadInstanceProperties(Properties props) {
        super.loadInstanceProperties(props);
        props.setProperty("eureka.client.discovery-endpoint.port", Integer.toString(config.getDiscoveryPort()));
    }

    public int getRegistrationPort() {
        // Since server might be started on the ephemeral port, we need to get it directly from RxNetty server
        return injector.getInstance(TcpRegistrationServer.class).serverPort();
    }

    public int getDiscoveryPort() {
        // Since server might be started on the ephemeral port, we need to get it directly from RxNetty server
        return injector.getInstance(TcpDiscoveryServer.class).serverPort();
    }

    public int getReplicationPort() {
        // Since server might be started on the ephemeral port, we need to get it directly from RxNetty server
        return injector.getInstance(TcpReplicationServer.class).serverPort();
    }

    public ServerResolver getRegistrationResolver() {
        return ServerResolvers.fromHostname("localhost").withPort(getRegistrationPort());
    }

    @Override
    public ServerResolver getInterestResolver() {
        return ServerResolvers.fromHostname("localhost").withPort(getDiscoveryPort());
    }

    @Override
    public WriteServerReport serverReport() {
        return new WriteServerReport(
                getRegistrationPort(),
                getDiscoveryPort(),
                getReplicationPort(),
                getEurekaServerRegistry().size(), getHttpServerPort(),
                getWebAdminPort()
        );
    }

    public static class WriteServerReport extends AbstractServerReport {
        private final int registrationPort;
        private final int discoveryPort;
        private final int replicationPort;
        private final int registrySize;

        public WriteServerReport(int registrationPort, int discoveryPort, int replicationPort,
                                 int registrySize, int httpServerPort, int adminPort) {
            super(httpServerPort, adminPort);
            this.registrationPort = registrationPort;
            this.discoveryPort = discoveryPort;
            this.replicationPort = replicationPort;
            this.registrySize = registrySize;
        }

        public int getRegistrationPort() {
            return registrationPort;
        }

        public int getDiscoveryPort() {
            return discoveryPort;
        }

        public int getReplicationPort() {
            return replicationPort;
        }

        public int getRegistrySize() {
            return registrySize;
        }
    }
}
