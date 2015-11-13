package com.netflix.eureka2.testkit.embedded;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.inject.Module;
import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.EurekaRegistrationClient;
import com.netflix.eureka2.client.Eurekas;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.client.resolver.ServerResolvers;
import com.netflix.eureka2.config.EurekaDashboardConfig;
import com.netflix.eureka2.config.EurekaTransportConfig;
import com.netflix.eureka2.model.datacenter.LocalDataCenterInfo.DataCenterType;
import com.netflix.eureka2.server.config.EurekaServerTransportConfig;
import com.netflix.eureka2.server.spi.ExtAbstractModule.ServerType;
import com.netflix.eureka2.testkit.embedded.cluster.EmbeddedReadCluster;
import com.netflix.eureka2.testkit.embedded.cluster.EmbeddedWriteCluster;
import com.netflix.eureka2.testkit.embedded.server.EmbeddedDashboardServer;
import com.netflix.eureka2.testkit.embedded.server.EmbeddedDashboardServerBuilder;
import com.netflix.eureka2.testkit.embedded.server.EmbeddedReadServer;
import com.netflix.eureka2.testkit.embedded.server.EmbeddedWriteServer;
import com.netflix.eureka2.testkit.embedded.view.ClusterViewHttpServer;
import com.netflix.eureka2.testkit.netrouter.NetworkRouter;
import com.netflix.eureka2.testkit.netrouter.NetworkRouters;

import static com.netflix.eureka2.config.bean.EurekaDashboardConfigBean.aDashboardConfig;
import static com.netflix.eureka2.model.interest.Interests.forVips;
import static com.netflix.eureka2.server.config.bean.EurekaClusterDiscoveryConfigBean.anEurekaClusterDiscoveryConfig;
import static com.netflix.eureka2.server.config.bean.EurekaInstanceInfoConfigBean.anEurekaInstanceInfoConfig;
import static com.netflix.eureka2.server.config.bean.EurekaServerRegistryConfigBean.anEurekaServerRegistryConfig;
import static com.netflix.eureka2.server.config.bean.EurekaServerTransportConfigBean.anEurekaServerTransportConfig;

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

        private EurekaServerTransportConfig transportConfig;
        private int writeClusterSize;
        private int readClusterSize;
        private boolean ephemeralPorts;
        private boolean networkRouterEnabled;
        private boolean dashboardEnabled;
        private boolean adminUIEnabled;
        private boolean extensionsEnabled;
        private Map<ServerType, List<Class<? extends Module>>> extensionModules;
        private boolean viewEnabled;
        private Map<ServerType, Map<Class<?>, Object>> configurationOverrides;

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

        public EurekaDeploymentBuilder withTransportConfig(EurekaServerTransportConfig transportConfig) {
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

        public EurekaDeploymentBuilder withExtensionModules(Map<ServerType, List<Class<? extends Module>>> extensionModules) {
            this.extensionModules = extensionModules;
            return this;
        }

        public EurekaDeploymentBuilder withConfiguration(Map<ServerType, Map<Class<?>, Object>> configurationOverrides) {
            this.configurationOverrides = configurationOverrides;
            return this;
        }

        public EurekaDeploymentBuilder withDeploymentView(boolean viewEnabled) {
            this.viewEnabled = viewEnabled;
            return this;
        }

        public EurekaDeployment build() {
            if (transportConfig == null) {
                transportConfig = anEurekaServerTransportConfig().build();
            }
            NetworkRouter networkRouter = networkRouterEnabled ? NetworkRouters.aRouter() : null;

            // Write cluster
            List<Class<? extends Module>> writeExtensions = extensionModules == null ? null : extensionModules.get(ServerType.Write);
            Map<Class<?>, Object> writeConfigOverrides = configurationOverrides == null ? null : configurationOverrides.get(ServerType.Write);
            EmbeddedWriteCluster writeCluster = new EmbeddedWriteCluster(writeExtensions,
                    extensionsEnabled, writeConfigOverrides,
                    adminUIEnabled, ephemeralPorts, transportConfig.getCodec(), networkRouter);
            writeCluster.scaleUpBy(writeClusterSize);

            // Read cluster
            List<Class<? extends Module>> readExtensions = extensionModules == null ? null : extensionModules.get(ServerType.Read);
            Map<Class<?>, Object> readConfigOverrides = configurationOverrides == null ? null : configurationOverrides.get(ServerType.Read);
            EmbeddedReadCluster readCluster = new EmbeddedReadCluster(writeCluster.registrationResolver(),
                    writeCluster.interestResolver(), readExtensions, extensionsEnabled,
                    readConfigOverrides,
                    adminUIEnabled, ephemeralPorts, transportConfig.getCodec(), networkRouter);
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

                EurekaDashboardConfig config = aDashboardConfig()
                        .withRegistryConfig(anEurekaServerRegistryConfig().build())
                        .withClusterDiscoveryConfig(anEurekaClusterDiscoveryConfig().build())
                        .withInstanceInfoConfig(
                                anEurekaInstanceInfoConfig()
                                        .withDataCenterType(DataCenterType.Basic)
                                        .withEurekaApplicationName(DASHBOARD_SERVER_NAME)
                                        .withEurekaVipAddress(DASHBOARD_SERVER_NAME)
                                        .build()
                        )
                        .withTransportConfig(
                                anEurekaServerTransportConfig(transportConfig)
                                        .withWebAdminPort(webAdminPort)
                                        .withShutDownPort(shutdownPort)
                                        .build()
                        )
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
