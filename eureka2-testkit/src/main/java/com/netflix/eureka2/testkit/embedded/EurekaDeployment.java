package com.netflix.eureka2.testkit.embedded;

import java.util.ArrayList;
import java.util.List;

import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.EurekaRegistrationClient;
import com.netflix.eureka2.client.Eurekas;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.client.resolver.ServerResolvers;
import com.netflix.eureka2.config.BasicEurekaTransportConfig;
import com.netflix.eureka2.config.EurekaDashboardConfig;
import com.netflix.eureka2.config.EurekaTransportConfig;
import com.netflix.eureka2.registry.datacenter.LocalDataCenterInfo.DataCenterType;
import com.netflix.eureka2.testkit.embedded.cluster.EmbeddedReadCluster;
import com.netflix.eureka2.testkit.embedded.cluster.EmbeddedWriteCluster;
import com.netflix.eureka2.testkit.embedded.server.EmbeddedDashboardServer;
import com.netflix.eureka2.testkit.embedded.server.EmbeddedDashboardServerBuilder;
import com.netflix.eureka2.testkit.embedded.server.EmbeddedReadServer;
import com.netflix.eureka2.testkit.embedded.server.EmbeddedWriteServer;
import com.netflix.eureka2.testkit.embedded.view.ClusterViewHttpServer;
import com.netflix.eureka2.testkit.netrouter.NetworkRouter;
import com.netflix.eureka2.testkit.netrouter.NetworkRouters;

import static com.netflix.eureka2.interests.Interests.forVips;

/**
 * @author Tomasz Bak
 */
public class EurekaDeployment {

    private static final String DASHBOARD_SERVER_NAME = "eureka2-dashboard";
    private static final int DASHBOARD_SERVER_PORTS_FROM = 16000;

    private final EurekaTransportConfig transportConfig;
    private final EmbeddedWriteCluster writeCluster;
    private final EmbeddedReadCluster readCluster;
    private final EmbeddedDashboardServer dashboardServer;
    private final NetworkRouter networkRouter;

    private final ClusterViewHttpServer deploymentView;

    private final List<EurekaInterestClient> connectedInterestClients = new ArrayList<>();
    private final List<EurekaRegistrationClient> connectedRegistrationClients = new ArrayList<>();

    protected EurekaDeployment(EurekaTransportConfig transportConfig,
                               EmbeddedWriteCluster writeCluster,
                               EmbeddedReadCluster readCluster,
                               EmbeddedDashboardServer dashboardServer,
                               NetworkRouter networkRouter,
                               boolean viewEnabled) {
        this.transportConfig = transportConfig;
        this.writeCluster = writeCluster;
        this.readCluster = readCluster;
        this.dashboardServer = dashboardServer;
        this.networkRouter = networkRouter;

        if (viewEnabled) {
            deploymentView = new ClusterViewHttpServer(this);
            deploymentView.start();
        } else {
            deploymentView = null;
        }
    }

    public EmbeddedWriteCluster getWriteCluster() {
        return writeCluster;
    }

    public EmbeddedReadCluster getReadCluster() {
        return readCluster;
    }

    public EmbeddedDashboardServer getDashboardServer() {
        return dashboardServer;
    }

    public NetworkRouter getNetworkRouter() {
        return networkRouter;
    }

    /**
     * Create a {@link EurekaRegistrationClient} instance to register with a particular write server
     *
     * @param idx id of a write server where to connect
     */
    public EurekaRegistrationClient registrationClientToWriteServer(int idx) {
        EmbeddedWriteServer server = getWriteCluster().getServer(idx);
        EurekaRegistrationClient registrationClient = Eurekas.newRegistrationClientBuilder()
                .withTransportConfig(transportConfig)
                .withServerResolver(server.getRegistrationResolver())
                .build();
        connectedRegistrationClients.add(registrationClient);
        return registrationClient;
    }

    /**
     * Create a {@link EurekaRegistrationClient} instance to register with any instance in a write cluster
     */
    public EurekaRegistrationClient registrationClientToWriteCluster() {
        EurekaRegistrationClient registrationClient = Eurekas.newRegistrationClientBuilder()
                .withTransportConfig(transportConfig)
                .withServerResolver(getWriteCluster().registrationResolver())
                .build();
        connectedRegistrationClients.add(registrationClient);
        return registrationClient;
    }

    /**
     * Create a {@link EurekaInterestClient} instance to do interest discovery with a particular write server
     *
     * @param idx id of a write server where to connect
     */
    public EurekaInterestClient interestClientToWriteServer(int idx) {
        EmbeddedWriteServer server = getWriteCluster().getServer(idx);
        EurekaInterestClient interestClient = Eurekas.newInterestClientBuilder()
                .withTransportConfig(transportConfig)
                .withServerResolver(server.getInterestResolver())
                .build();
        connectedInterestClients.add(interestClient);
        return interestClient;
    }

    /**
     * Create a {@link EurekaInterestClient} instance to do interest discovery with any instance in a write cluster
     */
    public EurekaInterestClient interestClientToWriteCluster() {
        EurekaInterestClient interestClient = Eurekas.newInterestClientBuilder()
                .withTransportConfig(transportConfig)
                .withServerResolver(getWriteCluster().interestResolver())
                .build();
        connectedInterestClients.add(interestClient);
        return interestClient;
    }

    /**
     * Create a {@link EurekaInterestClient} instance to do interest discovery with a particular read server
     *
     * @param idx id of a write server where to connect
     */
    public EurekaInterestClient interestClientToReadServer(int idx) {
        EmbeddedReadServer server = getReadCluster().getServer(idx);
        EurekaInterestClient interestClient = Eurekas.newInterestClientBuilder()
                .withTransportConfig(transportConfig)
                .withServerResolver(server.getInterestResolver())
                .build();
        connectedInterestClients.add(interestClient);
        return interestClient;
    }

    /**
     * Create a {@link EurekaInterestClient} instance to do interest discovery with any instance in a read cluster
     */
    public EurekaInterestClient interestClientToReadCluster() {
        EurekaInterestClient interestClient = Eurekas.newInterestClientBuilder()
                .withTransportConfig(transportConfig)
                .withServerResolver(getReadCluster().interestResolver())
                .build();
        connectedInterestClients.add(interestClient);
        return interestClient;
    }

    /**
     * Create a {@link EurekaInterestClient} instance to do interest discovery with any instance in a read cluster,
     * using the canonical method to first discover the read cluster from the write cluster
     */
    public EurekaInterestClient cannonicalInterestClient() {
        EurekaInterestClient interestClient = Eurekas.newInterestClientBuilder()
                .withTransportConfig(transportConfig)
                .withServerResolver(ServerResolvers.fromEureka(getWriteCluster().interestResolver())
                        .forInterest(forVips(getReadCluster().getVip())))
                .build();
        connectedInterestClients.add(interestClient);
        return interestClient;
    }

    public void shutdown() {
        for (EurekaInterestClient interestClient : connectedInterestClients) {
            interestClient.shutdown();
        }
        connectedInterestClients.clear();
        for (EurekaRegistrationClient registrationClient : connectedRegistrationClients) {
            registrationClient.shutdown();
        }
        connectedRegistrationClients.clear();

        writeCluster.shutdown();
        readCluster.shutdown();
        if (dashboardServer != null) {
            dashboardServer.shutdown();
        }
        if (deploymentView != null) {
            deploymentView.shutdown();
        }
    }

    public static class EurekaDeploymentBuilder {

        private EurekaTransportConfig transportConfig;
        private int writeClusterSize;
        private int readClusterSize;
        private boolean ephemeralPorts;
        private boolean networkRouterEnabled;
        private boolean dashboardEnabled;
        private boolean adminUIEnabled;
        private boolean extensionsEnabled;
        private boolean viewEnabled;

        public EurekaDeploymentBuilder withWriteClusterSize(int size) {
            writeClusterSize = size;
            return this;
        }

        public EurekaDeploymentBuilder withReadClusterSize(int size) {
            readClusterSize = size;
            return this;
        }

        public EurekaDeploymentBuilder withEphemeralPorts(boolean ephemeralPorts) {
            this.ephemeralPorts = ephemeralPorts;
            return this;
        }

        public EurekaDeploymentBuilder withNetworkRouter(boolean networkRouterEnabled) {
            this.networkRouterEnabled = networkRouterEnabled;
            return this;
        }

        public EurekaDeploymentBuilder withTransportConfig(EurekaTransportConfig transportConfig) {
            this.transportConfig = transportConfig;
            return this;
        }

        public EurekaDeploymentBuilder withDashboard(boolean dashboardEnabled) {
            this.dashboardEnabled = dashboardEnabled;
            return this;
        }

        public EurekaDeploymentBuilder withAdminUI(boolean adminUIEnabled) {
            this.adminUIEnabled = adminUIEnabled;
            return this;
        }

        public EurekaDeploymentBuilder withExtensions(boolean extensionsEnabled) {
            this.extensionsEnabled = extensionsEnabled;
            return this;
        }

        public EurekaDeploymentBuilder withDeploymentView(boolean viewEnabled) {
            this.viewEnabled = viewEnabled;
            return this;
        }

        public EurekaDeployment build() {
            if (transportConfig == null) {
                transportConfig = new BasicEurekaTransportConfig.Builder().build();
            }
            NetworkRouter networkRouter = networkRouterEnabled ? NetworkRouters.aRouter() : null;

            // Write cluster
            EmbeddedWriteCluster writeCluster = new EmbeddedWriteCluster(extensionsEnabled, adminUIEnabled, ephemeralPorts, transportConfig.getCodec(), networkRouter);
            writeCluster.scaleUpBy(writeClusterSize);

            // Read cluster
            EmbeddedReadCluster readCluster = new EmbeddedReadCluster(writeCluster.registrationResolver(),
                    writeCluster.interestResolver(), extensionsEnabled, adminUIEnabled, ephemeralPorts, transportConfig.getCodec(), networkRouter);
            readCluster.scaleUpBy(readClusterSize);

            // Dashboard
            EmbeddedDashboardServer dashboardServer = null;
            if (dashboardEnabled) {
                ServerResolver readClusterResolver;
                if (readClusterSize > 0) {
                    readClusterResolver = ServerResolvers.fromEureka(writeCluster.interestResolver()).forInterest(forVips(readCluster.getVip()));
                } else {
                    readClusterResolver = writeCluster.interestResolver();
                }

                int dashboardPort = ephemeralPorts ? 0 : DASHBOARD_SERVER_PORTS_FROM;
                int webAdminPort = ephemeralPorts ? 0 : DASHBOARD_SERVER_PORTS_FROM + 1;
                int shutdownPort = ephemeralPorts ? 0 : DASHBOARD_SERVER_PORTS_FROM + 2;

                EurekaDashboardConfig config = EurekaDashboardConfig.newBuilder()
                        .withAppName(DASHBOARD_SERVER_NAME)
                        .withVipAddress(DASHBOARD_SERVER_NAME)
                        .withDataCenterType(DataCenterType.Basic)
                        .withCodec(transportConfig.getCodec())
                        .withShutDownPort(shutdownPort)
                        .withWebAdminPort(webAdminPort)
                        .withDashboardPort(dashboardPort)
                        .build();

                dashboardServer = new EmbeddedDashboardServerBuilder()
                        .withConfiguration(config)
                        .withInterestResolver(readClusterResolver)
                        .withRegistrationResolver(writeCluster.registrationResolver())
                        .build();
            }
            return new EurekaDeployment(transportConfig, writeCluster, readCluster, dashboardServer, networkRouter, viewEnabled);
        }
    }
}
