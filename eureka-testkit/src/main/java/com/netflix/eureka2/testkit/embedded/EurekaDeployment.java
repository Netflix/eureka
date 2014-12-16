package com.netflix.eureka2.testkit.embedded;

import com.netflix.eureka2.testkit.embedded.cluster.EmbeddedReadCluster;
import com.netflix.eureka2.testkit.embedded.cluster.EmbeddedWriteCluster;
import com.netflix.eureka2.testkit.embedded.server.EmbeddedBridgeServer;
import com.netflix.eureka2.testkit.embedded.server.EmbeddedDashboardServer;
import com.netflix.eureka2.testkit.embedded.view.ClusterViewHttpServer;

/**
 * @author Tomasz Bak
 */
public class EurekaDeployment {

    private final EmbeddedWriteCluster writeCluster;
    private final EmbeddedReadCluster readCluster;
    private final EmbeddedBridgeServer bridgeServer;
    private final EmbeddedDashboardServer dashboardServer;

    private final ClusterViewHttpServer deploymentView;

    protected EurekaDeployment(EmbeddedWriteCluster writeCluster,
                               EmbeddedReadCluster readCluster,
                               EmbeddedBridgeServer bridgeServer,
                               EmbeddedDashboardServer dashboardServer,
                               boolean viewEnabled) {
        this.writeCluster = writeCluster;
        this.readCluster = readCluster;
        this.bridgeServer = bridgeServer;
        this.dashboardServer = dashboardServer;

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

    public EmbeddedBridgeServer getBridgeServer() {
        return bridgeServer;
    }

    public EmbeddedDashboardServer getDashboardServer() {
        return dashboardServer;
    }

    public void shutdown() {
        writeCluster.shutdown();
        readCluster.shutdown();
        if (bridgeServer != null) {
            bridgeServer.shutdown();
        }
        if (dashboardServer != null) {
            dashboardServer.shutdown();
        }
        if (deploymentView != null) {
            deploymentView.shutdown();
        }
    }

    public static class EurekaDeploymentBuilder {

        private int writeClusterSize;
        private int readClusterSize;
        private boolean bridgeEnabled;
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

        public EurekaDeploymentBuilder withBridge(boolean bridgeEnabled) {
            this.bridgeEnabled = bridgeEnabled;
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
            EmbeddedWriteCluster writeCluster = new EmbeddedWriteCluster(extensionsEnabled, adminUIEnabled);
            writeCluster.scaleUpBy(writeClusterSize);

            EmbeddedReadCluster readCluster = new EmbeddedReadCluster(writeCluster.registrationResolver(),
                    writeCluster.discoveryResolver(), extensionsEnabled, adminUIEnabled);
            readCluster.scaleUpBy(readClusterSize);

            EmbeddedBridgeServer bridgeServer = null;
            if (bridgeEnabled) {
                bridgeServer = EmbeddedBridgeServer.newBridge(writeCluster.replicationPeers(), extensionsEnabled, adminUIEnabled);
                bridgeServer.start();
            }
            EmbeddedDashboardServer dashboardServer = null;
            if (dashboardEnabled) {
                // TODO: read from the read cluster, not the write one
                dashboardServer = EmbeddedDashboardServer.newDashboard(
                        writeCluster.registrationResolver(),
                        writeCluster.discoveryResolver(),
//                    ServerResolvers.fromWriteServer(writeCluster.registrationResolver(), writeCluster.getVip()),
                        extensionsEnabled,
                        adminUIEnabled
                );
                dashboardServer.start();
            }
            return new EurekaDeployment(writeCluster, readCluster, bridgeServer, dashboardServer, viewEnabled);
        }
    }
}
