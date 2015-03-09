package com.netflix.eureka2.testkit.embedded.server;

import java.util.Properties;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.DiscoveryManager;
import com.netflix.eureka2.Server;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.client.resolver.ServerResolvers;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.registry.datacenter.LocalDataCenterInfo.DataCenterType;
import com.netflix.eureka2.server.EurekaBridgeServerModule;
import com.netflix.eureka2.server.ReplicationPeerAddressesProvider;
import com.netflix.eureka2.server.config.BridgeServerConfig;
import com.netflix.eureka2.server.transport.tcp.discovery.TcpDiscoveryServer;
import com.netflix.eureka2.server.transport.tcp.registration.TcpRegistrationServer;
import com.netflix.eureka2.server.transport.tcp.replication.TcpReplicationServer;
import com.netflix.eureka2.testkit.embedded.server.EmbeddedBridgeServer.BridgeServerReport;
import com.netflix.eureka2.transport.EurekaTransports.Codec;
import rx.Observable;

/**
 * @author Tomasz Bak
 */
public class EmbeddedBridgeServer extends EmbeddedEurekaServer<BridgeServerConfig, BridgeServerReport> {

    private static final String BRIDGE_SERVER_NAME = "eureka2-bridge";
    private static final int BRIDGE_SERVER_PORTS_FROM = 15000;

    private final Observable<ChangeNotification<Server>> replicationPeers;

    public EmbeddedBridgeServer(BridgeServerConfig config,
                                final Observable<ChangeNotification<Server>> replicationPeers,
                                boolean withExt,
                                boolean withDashboard) {
        super(config, withExt, withDashboard);
        this.replicationPeers = replicationPeers;
    }

    @Override
    public void start() {
        Module[] modules = {
                new EurekaBridgeServerModule(config),
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(ReplicationPeerAddressesProvider.class).toInstance(new ReplicationPeerAddressesProvider(replicationPeers));
                    }
                }
        };

        setup(modules);
    }

    @Override
    public ServerResolver getInterestResolver() {
        return ServerResolvers.withHostname("localhost").withPort(getDiscoveryPort());
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

    @Override
    public BridgeServerReport serverReport() {
        return new BridgeServerReport(
                config.getRegistrationPort(),
                config.getDiscoveryPort(),
                config.getReplicationPort(),
                getHttpServerPort(),
                getWebAdminPort(),
                DiscoveryClient.getRegion(),
                DiscoveryManager.getInstance().getEurekaClientConfig().getEurekaServerDNSName()
        );
    }

    public static EmbeddedBridgeServer newBridge(final Observable<ChangeNotification<Server>> replicationPeers,
                                                 boolean withExt,
                                                 boolean withDashboard) {
        return newBridge(replicationPeers, withExt, withDashboard, Codec.Avro);
    }

    public static EmbeddedBridgeServer newBridge(final Observable<ChangeNotification<Server>> replicationPeers,
                                                 boolean withExt,
                                                 boolean withDashboard,
                                                 Codec codec) {
        BridgeServerConfig config = BridgeServerConfig.newBuilder()
                .withAppName(BRIDGE_SERVER_NAME)
                .withVipAddress(BRIDGE_SERVER_NAME)
                .withDataCenterType(DataCenterType.Basic)
                .withRegistrationPort(BRIDGE_SERVER_PORTS_FROM)
                .withDiscoveryPort(BRIDGE_SERVER_PORTS_FROM + 1)  // explicitly set it to a different port to verify
                .withReplicationPort(BRIDGE_SERVER_PORTS_FROM + 2)  // explicitly set it to a different port to verify
                .withCodec(codec)
                .withRefreshRateSec(30)
                .withShutDownPort(BRIDGE_SERVER_PORTS_FROM + 3)
                .withWebAdminPort(BRIDGE_SERVER_PORTS_FROM + 4)
                .build();
        return new EmbeddedBridgeServer(config, replicationPeers, withExt, withDashboard);
    }

    public static class BridgeServerReport extends AbstractServerReport {

        private final int registrationPort;
        private final int discoveryPort;
        private final int replicationPort;
        private final String bridgedRegion;
        private final String eureka1DNSName;

        public BridgeServerReport(int registrationPort,
                                  int discoveryPort,
                                  int replicationPort,
                                  int httpServerPort,
                                  int adminPort,
                                  String bridgedRegion,
                                  String eureka1DNSName) {
            super(httpServerPort, adminPort);
            this.registrationPort = registrationPort;
            this.discoveryPort = discoveryPort;
            this.replicationPort = replicationPort;
            this.bridgedRegion = bridgedRegion;
            this.eureka1DNSName = eureka1DNSName;
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

        public String getBridgedRegion() {
            return bridgedRegion;
        }

        public String getEureka1DNSName() {
            return eureka1DNSName;
        }
    }
}
