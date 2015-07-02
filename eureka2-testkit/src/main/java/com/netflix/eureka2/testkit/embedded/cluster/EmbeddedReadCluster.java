package com.netflix.eureka2.testkit.embedded.cluster;

import java.util.ArrayList;
import java.util.List;

import com.netflix.eureka2.Server;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.client.resolver.ServerResolvers;
import com.netflix.eureka2.codec.CodecType;
import com.netflix.eureka2.server.config.EurekaServerConfig;
import com.netflix.eureka2.testkit.embedded.cluster.EmbeddedReadCluster.ReadClusterReport;
import com.netflix.eureka2.testkit.embedded.server.EmbeddedReadServer;
import com.netflix.eureka2.testkit.embedded.server.EmbeddedReadServer.ReadServerReport;
import com.netflix.eureka2.testkit.embedded.server.EmbeddedReadServerBuilder;
import com.netflix.eureka2.testkit.netrouter.NetworkRouter;

import static com.netflix.eureka2.server.config.bean.EurekaClusterDiscoveryConfigBean.anEurekaClusterDiscoveryConfig;
import static com.netflix.eureka2.server.config.bean.EurekaInstanceInfoConfigBean.anEurekaInstanceInfoConfig;
import static com.netflix.eureka2.server.config.bean.EurekaServerConfigBean.anEurekaServerConfig;
import static com.netflix.eureka2.server.config.bean.EurekaServerTransportConfigBean.anEurekaServerTransportConfig;

/**
 * @author Tomasz Bak
 */
public class EmbeddedReadCluster extends EmbeddedEurekaCluster<EmbeddedReadServer, Server, ReadClusterReport> {

    public static final String READ_SERVER_NAME = "eureka2-read";
    public static final int READ_SERVER_PORTS_FROM = 14000;

    private final ServerResolver registrationResolver;
    private final ServerResolver interestResolver;
    private final boolean ext;
    private final boolean adminUI;
    private final boolean ephemeralPorts;
    private final CodecType codec;
    private final NetworkRouter networkRouter;

    private int nextAvailablePort = READ_SERVER_PORTS_FROM;

    public EmbeddedReadCluster(ServerResolver registrationResolver,
                               ServerResolver interestResolver,
                               boolean ext,
                               boolean adminUI,
                               boolean ephemeralPorts,
                               NetworkRouter networkRouter) {
        this(registrationResolver, interestResolver, ext, adminUI, ephemeralPorts, CodecType.Avro, networkRouter);
    }

    public EmbeddedReadCluster(ServerResolver registrationResolver,
                               ServerResolver interestResolver,
                               boolean ext,
                               boolean adminUI,
                               boolean ephemeralPorts,
                               CodecType codec,
                               NetworkRouter networkRouter) {
        super(READ_SERVER_NAME);
        this.registrationResolver = registrationResolver;
        this.interestResolver = interestResolver;
        this.ext = ext;
        this.adminUI = adminUI;
        this.ephemeralPorts = ephemeralPorts;
        this.codec = codec;
        this.networkRouter = networkRouter;
    }

    @Override
    public int scaleUpByOne() {
        int discoveryPort = ephemeralPorts ? 0 : nextAvailablePort;
        int httpPort = ephemeralPorts ? 0 : nextAvailablePort + 1;
        int adminPort = ephemeralPorts ? 0 : nextAvailablePort + 2;

        EurekaServerConfig config = anEurekaServerConfig()
                .withInstanceInfoConfig(
                        anEurekaInstanceInfoConfig()
                                .withEurekaApplicationName(READ_SERVER_NAME)
                                .withEurekaVipAddress(READ_SERVER_NAME)
                                .build()
                )
                .withTransportConfig(
                        anEurekaServerTransportConfig()
                                .withCodec(codec)
                                .withHttpPort(httpPort)
                                .withInterestPort(discoveryPort)
                                .withShutDownPort(0)
                                .withWebAdminPort(adminPort)
                                .build()
                )
                .build();

        EmbeddedReadServer newServer = newServer(config);
        nextAvailablePort += 10;

        if (ephemeralPorts) {
            discoveryPort = newServer.getInterestPort();
        }

        return scaleUpByOne(newServer, new Server("localhost", discoveryPort));
    }

    protected EmbeddedReadServer newServer(EurekaServerConfig config) {
        return new EmbeddedReadServerBuilder(nextAvailableServerId())
                .withConfiguration(config)
                .withRegistrationResolver(registrationResolver)
                .withInterestResolver(interestResolver)
                .withNetworkRouter(networkRouter)
                .withAdminUI(adminUI)
                .withExt(ext)
                .build();
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
