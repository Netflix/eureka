package com.netflix.eureka2.testkit.embedded.cluster;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.inject.Module;
import com.netflix.eureka2.model.Server;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.client.resolver.ServerResolvers;
import com.netflix.eureka2.codec.CodecType;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.model.notification.ChangeNotification.Kind;
import com.netflix.eureka2.server.config.WriteServerConfig;
import com.netflix.eureka2.server.resolver.ClusterAddress;
import com.netflix.eureka2.server.resolver.ClusterAddress.ServiceType;
import com.netflix.eureka2.testkit.embedded.cluster.EmbeddedWriteCluster.WriteClusterReport;
import com.netflix.eureka2.testkit.embedded.server.EmbeddedWriteServer;
import com.netflix.eureka2.testkit.embedded.server.EmbeddedWriteServer.WriteServerReport;
import com.netflix.eureka2.testkit.embedded.server.EmbeddedWriteServerBuilder;
import com.netflix.eureka2.testkit.netrouter.NetworkRouter;
import com.netflix.eureka2.utils.functions.RxFunctions;
import rx.Observable;
import rx.functions.Func1;

import static com.netflix.eureka2.server.config.bean.BootstrapConfigBean.aBootstrapConfig;
import static com.netflix.eureka2.server.config.bean.EurekaInstanceInfoConfigBean.anEurekaInstanceInfoConfig;
import static com.netflix.eureka2.server.config.bean.EurekaServerTransportConfigBean.anEurekaServerTransportConfig;
import static com.netflix.eureka2.server.config.bean.WriteServerConfigBean.aWriteServerConfig;

/**
 * @author Tomasz Bak
 */
public class EmbeddedWriteCluster extends EmbeddedEurekaCluster<EmbeddedWriteServer, ClusterAddress, WriteClusterReport> {

    public static final AtomicInteger WRITE_SERVER_ID = new AtomicInteger(0);
    public static final String WRITE_SERVER_NAME = "eureka2-write";
    public static final int WRITE_SERVER_PORTS_FROM = 13000;

    private final List<Class<? extends Module>> extensionModules;
    private final boolean withExt;
    private final Map<Class<?>, Object> configurationOverrides;
    private final boolean withAdminUI;
    private final boolean ephemeralPorts;
    private final CodecType codec;
    private final NetworkRouter networkRouter;

    private int nextAvailablePort = WRITE_SERVER_PORTS_FROM;

    public EmbeddedWriteCluster(List<Class<? extends Module>> extensionModules, boolean withExt,
                                Map<Class<?>, Object> configurationOverrides, boolean withAdminUI,
                                boolean ephemeralPorts, CodecType codec, NetworkRouter networkRouter) {
        super(WRITE_SERVER_NAME);
        this.extensionModules = extensionModules;
        this.withExt = withExt;
        this.configurationOverrides = configurationOverrides;
        this.withAdminUI = withAdminUI;
        this.ephemeralPorts = ephemeralPorts;
        this.codec = codec;
        this.networkRouter = networkRouter;
    }

    @Override
    public int scaleUpByOne() {
        ClusterAddress writeServerAddress = ephemeralPorts ?
                ClusterAddress.valueOf("localhost", 0, 0, 0) :
                ClusterAddress.valueOf("localhost", nextAvailablePort, nextAvailablePort + 1, nextAvailablePort + 2);

        int httpPort = ephemeralPorts ? 0 : nextAvailablePort + 3;
        int adminPort = ephemeralPorts ? 0 : nextAvailablePort + 4;

        WriteServerConfig config = aWriteServerConfig()
                .withInstanceInfoConfig(
                        anEurekaInstanceInfoConfig()
                                .withUniqueId("" + WRITE_SERVER_ID.getAndIncrement())
                                .withEurekaApplicationName(WRITE_SERVER_NAME)
                                .withEurekaVipAddress(WRITE_SERVER_NAME)
                                .build()
                )
                .withTransportConfig(
                        anEurekaServerTransportConfig()
                                .withCodec(codec)
                                .withHttpPort(httpPort)
                                .withInterestPort(writeServerAddress.getInterestPort())
                                .withRegistrationPort(writeServerAddress.getRegistrationPort())
                                .withReplicationPort(writeServerAddress.getReplicationPort())
                                .withShutDownPort(0)
                                .withWebAdminPort(adminPort)
                                .build()
                )
                .withBootstrapConfig(aBootstrapConfig().withBootstrapEnabled(false).build())
                .build();

        EmbeddedWriteServer writeServer = newServer(config);

        nextAvailablePort += 10;

        if (ephemeralPorts) {
            writeServerAddress = ClusterAddress.valueOf("localhost", writeServer.getRegistrationPort(),
                    writeServer.getInterestPort(), writeServer.getReplicationPort());
        }

        return scaleUpByOne(writeServer, writeServerAddress);
    }

    protected EmbeddedWriteServer newServer(WriteServerConfig config) {
        return new EmbeddedWriteServerBuilder()
                .withConfiguration(config)
                .withReplicationPeers(resolvePeers(ServiceType.Replication))
                .withNetworkRouter(networkRouter)
                .withAdminUI(withAdminUI)
                .withExt(withExt)
                .withExtensionModules(extensionModules)
                .withConfigurationOverrides(configurationOverrides)
                .build();
    }

    @Override
    public void scaleDownByOne(int idx) {
        super.scaleDownByOne(idx);
    }

    @Override
    public WriteClusterReport clusterReport() {
        List<WriteServerReport> serverReports = new ArrayList<>();
        for (EmbeddedWriteServer server : servers) {
            serverReports.add(server.serverReport());
        }
        return new WriteClusterReport(serverReports);
    }

    public ServerResolver registrationResolver() {
        return getServerResolver(new Func1<ClusterAddress, Integer>() {
            @Override
            public Integer call(ClusterAddress writeServerAddress) {
                return writeServerAddress.getRegistrationPort();
            }
        });
    }

    public ServerResolver interestResolver() {
        return getServerResolver(new Func1<ClusterAddress, Integer>() {
            @Override
            public Integer call(ClusterAddress writeServerAddress) {
                return writeServerAddress.getInterestPort();
            }
        });
    }

    public Observable<ChangeNotification<Server>> resolvePeers(final ServiceType serviceType) {
        return clusterChangeObservable().map(
                new Func1<ChangeNotification<ClusterAddress>, ChangeNotification<Server>>() {
                    @Override
                    public ChangeNotification<Server> call(ChangeNotification<ClusterAddress> notification) {
                        if (notification.getKind() == Kind.BufferSentinel) {
                            return null;
                        }

                        ClusterAddress data = notification.getData();
                        int port;
                        switch (serviceType) {
                            case Registration:
                                port = data.getRegistrationPort();
                                break;
                            case Interest:
                                port = data.getInterestPort();
                                break;
                            case Replication:
                                port = data.getReplicationPort();
                                break;
                            default:
                                throw new IllegalStateException("Unexpected enum value " + serviceType);
                        }
                        Server serverAddress = new Server(data.getHostName(), port);
                        switch (notification.getKind()) {
                            case Add:
                                return new ChangeNotification<Server>(Kind.Add, serverAddress);
                            case Modify:
                                throw new IllegalStateException("Modify not expected");
                            case Delete:
                                return new ChangeNotification<Server>(Kind.Delete, serverAddress);
                        }
                        return null;
                    }
                }).filter(RxFunctions.filterNullValuesFunc());
    }

    private ServerResolver getServerResolver(final Func1<ClusterAddress, Integer> portFunc) {
        Observable<ChangeNotification<Server>> serverSource = clusterChangeObservable().map(new Func1<ChangeNotification<ClusterAddress>, ChangeNotification<Server>>() {
            @Override
            public ChangeNotification<Server> call(ChangeNotification<ClusterAddress> notification) {
                if (notification.getKind() == Kind.BufferSentinel) {
                    return ChangeNotification.bufferSentinel();
                }

                ClusterAddress endpoints = notification.getData();
                int port = portFunc.call(endpoints);
                switch (notification.getKind()) {
                    case Add:
                        return new ChangeNotification<>(Kind.Add, new Server(endpoints.getHostName(), port));
                    case Modify:
                        throw new IllegalStateException("Modify not expected");
                    case Delete:
                        return new ChangeNotification<>(Kind.Delete, new Server(endpoints.getHostName(), port));
                    default:
                        //no-op
                }
                return null;
            }
        }).filter(RxFunctions.filterNullValuesFunc());

        return ServerResolvers.fromServerSource(serverSource);
    }

    public static class WriteClusterReport {
        private final List<WriteServerReport> serverReports;

        public WriteClusterReport(List<WriteServerReport> serverReports) {
            this.serverReports = serverReports;
        }

        public List<WriteServerReport> getServerReports() {
            return serverReports;
        }
    }
}
