package com.netflix.eureka2.testkit.embedded.server;

import java.net.InetSocketAddress;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.DiscoveryManager;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.registry.datacenter.LocalDataCenterInfo.DataCenterType;
import com.netflix.eureka2.server.EurekaBridgeServerModule;
import com.netflix.eureka2.server.ReplicationPeerAddressesProvider;
import com.netflix.eureka2.server.config.BridgeServerConfig;
import com.netflix.eureka2.testkit.embedded.server.EmbeddedBridgeServer.BridgeServerReport;
import com.netflix.eureka2.transport.EurekaTransports.Codec;
import rx.Observable;

/**
 * @author Tomasz Bak
 */
public class EmbeddedBridgeServer extends EmbeddedEurekaServer<BridgeServerConfig, BridgeServerReport> {

    private static final String BRIDGE_SERVER_NAME = "eureka2-bridge";
    private static final int BRIDGE_SERVER_PORTS_FROM = 15000;

    public EmbeddedBridgeServer(BridgeServerConfig config,
                                final Observable<ChangeNotification<InetSocketAddress>> replicationPeers,
                                boolean withExt,
                                boolean withDashboard) {
        super(config, withExt, withDashboard);
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
    public BridgeServerReport serverReport() {
        return new BridgeServerReport(
                config.getRegistrationPort(),
                config.getDiscoveryPort(),
                config.getReplicationPort(),
                formatAdminURI(),
                DiscoveryClient.getRegion(),
                DiscoveryManager.getInstance().getEurekaClientConfig().getEurekaServerDNSName()
        );
    }

    public static EmbeddedBridgeServer newBridge(final Observable<ChangeNotification<InetSocketAddress>> replicationPeers,
                                                 boolean withExt,
                                                 boolean withDashboard) {
        BridgeServerConfig config = BridgeServerConfig.newBuilder()
                .withAppName(BRIDGE_SERVER_NAME)
                .withVipAddress(BRIDGE_SERVER_NAME)
                .withDataCenterType(DataCenterType.Basic)
                .withRegistrationPort(BRIDGE_SERVER_PORTS_FROM)
                .withDiscoveryPort(BRIDGE_SERVER_PORTS_FROM + 1)  // explicitly set it to a different port to verify
                .withReplicationPort(BRIDGE_SERVER_PORTS_FROM + 2)  // explicitly set it to a different port to verify
                .withCodec(Codec.Avro)
                .withRefreshRateSec(30)
                .withShutDownPort(BRIDGE_SERVER_PORTS_FROM + 3)
                .withWebAdminPort(BRIDGE_SERVER_PORTS_FROM + 4)
                .build();
        return new EmbeddedBridgeServer(config, replicationPeers, withExt, withDashboard);
    }

    public static class BridgeServerReport {

        private final int registrationPort;
        private final int discoveryPort;
        private final int replicationPort;
        private final String adminURI;
        private final String bridgedRegion;
        private final String eureka1DNSName;

        public BridgeServerReport(int registrationPort,
                                  int discoveryPort,
                                  int replicationPort,
                                  String adminURI,
                                  String bridgedRegion,
                                  String eureka1DNSName) {
            this.registrationPort = registrationPort;
            this.discoveryPort = discoveryPort;
            this.replicationPort = replicationPort;
            this.adminURI = adminURI;
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

        public String getAdminURI() {
            return adminURI;
        }

        public String getBridgedRegion() {
            return bridgedRegion;
        }

        public String getEureka1DNSName() {
            return eureka1DNSName;
        }
    }
}
