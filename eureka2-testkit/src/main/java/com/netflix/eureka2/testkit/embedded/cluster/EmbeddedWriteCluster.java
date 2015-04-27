package com.netflix.eureka2.testkit.embedded.cluster;

import java.util.ArrayList;
import java.util.List;

import com.netflix.eureka2.Server;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.client.resolver.ServerResolvers;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.registry.datacenter.LocalDataCenterInfo.DataCenterType;
import com.netflix.eureka2.server.resolver.EurekaEndpoint.ServiceType;
import com.netflix.eureka2.server.config.WriteServerConfig;
import com.netflix.eureka2.testkit.embedded.cluster.EmbeddedWriteCluster.WriteClusterReport;
import com.netflix.eureka2.testkit.embedded.cluster.EmbeddedWriteCluster.WriteServerAddress;
import com.netflix.eureka2.testkit.embedded.server.EmbeddedWriteServer;
import com.netflix.eureka2.testkit.embedded.server.EmbeddedWriteServer.WriteServerReport;
import com.netflix.eureka2.codec.CodecType;
import com.netflix.eureka2.utils.rx.RxFunctions;
import rx.Observable;
import rx.functions.Func1;

/**
 * @author Tomasz Bak
 */
public class EmbeddedWriteCluster extends EmbeddedEurekaCluster<EmbeddedWriteServer, WriteServerAddress, WriteClusterReport> {

    public static final String WRITE_SERVER_NAME = "eureka2-write";
    public static final int WRITE_SERVER_PORTS_FROM = 13000;

    private final boolean withExt;
    private final boolean withAdminUI;
    private final boolean ephemeralPorts;
    private final CodecType codec;

    private int nextAvailablePort = WRITE_SERVER_PORTS_FROM;

    public EmbeddedWriteCluster(boolean withExt, boolean withAdminUI, boolean ephemeralPorts) {
        this(withExt, withAdminUI, ephemeralPorts, CodecType.Avro);
    }

    public EmbeddedWriteCluster(boolean withExt, boolean withAdminUI, boolean ephemeralPorts, CodecType codec) {
        super(WRITE_SERVER_NAME);
        this.withExt = withExt;
        this.withAdminUI = withAdminUI;
        this.ephemeralPorts = ephemeralPorts;
        this.codec = codec;
    }

    @Override
    public int scaleUpByOne() {
        WriteServerAddress writeServerAddress = ephemeralPorts ?
                new WriteServerAddress("localhost", 0, 0, 0) :
                new WriteServerAddress("localhost", nextAvailablePort, nextAvailablePort + 1, nextAvailablePort + 2);

        int httpPort = ephemeralPorts ? 0 : nextAvailablePort + 3;
        int adminPort = ephemeralPorts ? 0 : nextAvailablePort + 4;

        WriteServerConfig config = WriteServerConfig.writeBuilder()
                .withAppName(WRITE_SERVER_NAME)
                .withVipAddress(WRITE_SERVER_NAME)
                .withReadClusterVipAddress(EmbeddedReadCluster.READ_SERVER_NAME)
                .withDataCenterType(DataCenterType.Basic)
                .withRegistrationPort(writeServerAddress.getRegistrationPort())
                .withDiscoveryPort(writeServerAddress.getDiscoveryPort())
                .withReplicationPort(writeServerAddress.getReplicationPort())
                .withServerList(new String[]{writeServerAddress.toString()})
                .withCodec(codec)
                .withHttpPort(httpPort)
                .withShutDownPort(0) // We do not run shutdown service in embedded server
                .withWebAdminPort(adminPort)
                .withReplicationRetryMillis(1000)
                .build();
        EmbeddedWriteServer newServer = newServer(config);
        newServer.start();

        nextAvailablePort += 10;

        if (ephemeralPorts) {
            writeServerAddress = new WriteServerAddress("localhost", newServer.getRegistrationPort(),
                    newServer.getDiscoveryPort(), newServer.getReplicationPort());
        }

        return scaleUpByOne(newServer, writeServerAddress);
    }

    protected EmbeddedWriteServer newServer(WriteServerConfig config) {
        return new EmbeddedWriteServer(
                config,
                resolvePeers(ServiceType.Interest),
                resolvePeers(ServiceType.Replication),
                withExt,
                withAdminUI
        );
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
        return getServerResolver(new Func1<WriteServerAddress, Integer>() {
            @Override
            public Integer call(WriteServerAddress writeServerAddress) {
                return writeServerAddress.getRegistrationPort();
            }
        });
    }

    public ServerResolver interestResolver() {
        return getServerResolver(new Func1<WriteServerAddress, Integer>() {
            @Override
            public Integer call(WriteServerAddress writeServerAddress) {
                return writeServerAddress.getDiscoveryPort();
            }
        });
    }

    public Observable<ChangeNotification<Server>> resolvePeers(final ServiceType serviceType) {
        return clusterChangeObservable().map(
                new Func1<ChangeNotification<WriteServerAddress>, ChangeNotification<Server>>() {
                    @Override
                    public ChangeNotification<Server> call(ChangeNotification<WriteServerAddress> notification) {
                        if (notification.getKind() == Kind.BufferSentinel) {
                            return null;
                        }

                        WriteServerAddress data = notification.getData();
                        int port;
                        switch (serviceType) {
                            case Registration:
                                port = data.getRegistrationPort();
                                break;
                            case Interest:
                                port = data.getDiscoveryPort();
                                break;
                            default: // == Replication
                                port = data.getReplicationPort();
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

    private ServerResolver getServerResolver(final Func1<WriteServerAddress, Integer> portFunc) {
        Observable<ChangeNotification<Server>> serverSource = clusterChangeObservable().map(new Func1<ChangeNotification<WriteServerAddress>, ChangeNotification<Server>>() {
            @Override
            public ChangeNotification<Server> call(ChangeNotification<WriteServerAddress> notification) {
                if (notification.getKind() == Kind.BufferSentinel) {
                    return ChangeNotification.bufferSentinel();
                }

                WriteServerAddress endpoints = notification.getData();
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

    public static class WriteServerAddress {

        private final String hostName;
        private final int registrationPort;
        private final int discoveryPort;
        private final int replicationPort;

        WriteServerAddress(String hostName, int registrationPort, int discoveryPort, int replicationPort) {
            this.hostName = hostName;
            this.registrationPort = registrationPort;
            this.discoveryPort = discoveryPort;
            this.replicationPort = replicationPort;
        }

        public String getHostName() {
            return hostName;
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

        @Override
        public String toString() {
            return getHostName() + ':' + getRegistrationPort() + ':' + getDiscoveryPort() + ':' + getReplicationPort();
        }
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
