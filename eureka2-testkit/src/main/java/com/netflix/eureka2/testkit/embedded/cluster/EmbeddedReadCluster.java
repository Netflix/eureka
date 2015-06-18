package com.netflix.eureka2.testkit.embedded.cluster;

import java.util.ArrayList;
import java.util.List;

import com.netflix.eureka2.Server;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.client.resolver.ServerResolvers;
import com.netflix.eureka2.codec.CodecType;
import com.netflix.eureka2.registry.datacenter.LocalDataCenterInfo.DataCenterType;
import com.netflix.eureka2.server.config.EurekaServerConfig;
import com.netflix.eureka2.testkit.embedded.cluster.EmbeddedReadCluster.ReadClusterReport;
import com.netflix.eureka2.testkit.embedded.server.EmbeddedReadServer;
import com.netflix.eureka2.testkit.embedded.server.EmbeddedReadServer.ReadServerReport;
import com.netflix.eureka2.testkit.netrouter.NetworkRouter;

/**
 * @author Tomasz Bak
 */
public class EmbeddedReadCluster extends EmbeddedEurekaCluster<EmbeddedReadServer, Server, ReadClusterReport> {

    public static final String READ_SERVER_NAME = "eureka2-read";
    public static final int READ_SERVER_PORTS_FROM = 14000;

    private final ServerResolver registrationResolver;
    private final ServerResolver discoveryResolver;
    private final boolean withExt;
    private final boolean withAdminUI;
    private final boolean ephemeralPorts;
    private final CodecType codec;
    private final NetworkRouter networkRouter;

    private int nextAvailablePort = READ_SERVER_PORTS_FROM;

    public EmbeddedReadCluster(ServerResolver registrationResolver,
                               ServerResolver discoveryResolver,
                               boolean withExt,
                               boolean withAdminUI,
                               boolean ephemeralPorts,
                               NetworkRouter networkRouter) {
        this(registrationResolver, discoveryResolver, withExt, withAdminUI, ephemeralPorts, CodecType.Avro, networkRouter);
    }

    public EmbeddedReadCluster(ServerResolver registrationResolver,
                               ServerResolver discoveryResolver,
                               boolean withExt,
                               boolean withAdminUI,
                               boolean ephemeralPorts,
                               CodecType codec,
                               NetworkRouter networkRouter) {
        super(READ_SERVER_NAME);
        this.registrationResolver = registrationResolver;
        this.discoveryResolver = discoveryResolver;
        this.withExt = withExt;
        this.withAdminUI = withAdminUI;
        this.ephemeralPorts = ephemeralPorts;
        this.codec = codec;
        this.networkRouter = networkRouter;
    }

    @Override
    public int scaleUpByOne() {
        int discoveryPort = ephemeralPorts ? 0 : nextAvailablePort;
        int httpPort = ephemeralPorts ? 0 : nextAvailablePort + 1;
        int adminPort = ephemeralPorts ? 0 : nextAvailablePort + 2;

        EurekaServerConfig config = EurekaServerConfig.baseBuilder()
                .withAppName(READ_SERVER_NAME)
                .withVipAddress(READ_SERVER_NAME)
                .withReadClusterVipAddress(READ_SERVER_NAME)
                .withDataCenterType(DataCenterType.Basic)
                .withDiscoveryPort(discoveryPort)
                .withHttpPort(httpPort)
                .withShutDownPort(0) // We do not run shutdown service in embedded server
                .withWebAdminPort(adminPort)
                .withCodec(codec)
                .build();

        EmbeddedReadServer newServer = newServer(config);
        newServer.start();

        nextAvailablePort += 10;

        if (ephemeralPorts) {
            discoveryPort = newServer.getDiscoveryPort();
        }

        return scaleUpByOne(newServer, new Server("localhost", discoveryPort));
    }

    protected EmbeddedReadServer newServer(EurekaServerConfig config) {
        return new EmbeddedReadServer(
                nextAvailableServerId(),
                config,
                registrationResolver,
                discoveryResolver,
                networkRouter,
                withExt,
                withAdminUI
        );
    }

    @Override
    public ReadClusterReport clusterReport() {
        List<ReadServerReport> serverReports = new ArrayList<>();
        for (EmbeddedReadServer server : servers) {
            serverReports.add(server.serverReport());
        }
        return new ReadClusterReport(serverReports);
    }

    public ServerResolver interestResolver() {
        return ServerResolvers.fromServerSource(clusterChangeObservable());
    }

    public static class ReadClusterReport {

        private final List<ReadServerReport> serverReports;

        public ReadClusterReport(List<ReadServerReport> serverReports) {
            this.serverReports = serverReports;
        }

        public List<ReadServerReport> getServerReports() {
            return serverReports;
        }
    }
}
