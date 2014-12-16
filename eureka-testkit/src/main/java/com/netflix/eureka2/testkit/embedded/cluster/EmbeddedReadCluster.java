package com.netflix.eureka2.testkit.embedded.cluster;

import java.util.ArrayList;
import java.util.List;

import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.registry.datacenter.LocalDataCenterInfo.DataCenterType;
import com.netflix.eureka2.server.config.EurekaServerConfig;
import com.netflix.eureka2.testkit.embedded.cluster.EmbeddedReadCluster.ReadClusterReport;
import com.netflix.eureka2.testkit.embedded.server.EmbeddedReadServer;
import com.netflix.eureka2.testkit.embedded.server.EmbeddedReadServer.ReadServerReport;
import com.netflix.eureka2.transport.EurekaTransports.Codec;

/**
 * @author Tomasz Bak
 */
public class EmbeddedReadCluster extends EmbeddedEurekaCluster<EmbeddedReadServer, ReadClusterReport> {

    private static final String READ_SERVER_NAME = "eureka2-read";
    private static final int READ_SERVER_PORTS_FROM = 14000;

    private final ServerResolver registrationResolver;
    private final ServerResolver discoveryResolver;
    private final boolean withExt;
    private final boolean withAdminUI;

    private int nextAvailablePort = READ_SERVER_PORTS_FROM;

    public EmbeddedReadCluster(ServerResolver registrationResolver,
                               ServerResolver discoveryResolver,
                               boolean withExt,
                               boolean withAdminUI) {
        super(READ_SERVER_NAME);
        this.registrationResolver = registrationResolver;
        this.discoveryResolver = discoveryResolver;
        this.withExt = withExt;
        this.withAdminUI = withAdminUI;
    }

    @Override
    public int scaleUpByOne() {
        EurekaServerConfig config = EurekaServerConfig.baseBuilder()
                .withAppName(READ_SERVER_NAME)
                .withVipAddress(READ_SERVER_NAME)
                .withDataCenterType(DataCenterType.Basic)
                .withDiscoveryPort(nextAvailablePort)
                .withShutDownPort(nextAvailablePort + 1)
                .withWebAdminPort(nextAvailablePort + 2)
                .withCodec(Codec.Avro)
                .build();
        EmbeddedReadServer newServer = newServer(config);
        newServer.start();

        servers.add(newServer);

        nextAvailablePort += 10;

        return servers.size() - 1;
    }

    protected EmbeddedReadServer newServer(EurekaServerConfig config) {
        return new EmbeddedReadServer(config, registrationResolver, discoveryResolver, withExt, withAdminUI);
    }

    @Override
    public ReadClusterReport clusterReport() {
        List<ReadServerReport> serverReports = new ArrayList<>();
        for (EmbeddedReadServer server : servers) {
            serverReports.add(server.serverReport());
        }
        return new ReadClusterReport(serverReports);
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
