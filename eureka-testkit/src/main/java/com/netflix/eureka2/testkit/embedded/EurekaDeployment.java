package com.netflix.eureka2.testkit.embedded;

import com.netflix.eureka2.testkit.embedded.cluster.EmbeddedReadCluster;
import com.netflix.eureka2.testkit.embedded.cluster.EmbeddedWriteCluster;
import com.netflix.eureka2.testkit.embedded.server.EmbeddedBridgeServer;
import com.netflix.eureka2.testkit.embedded.server.EmbeddedDashboardServer;

/**
 * @author Tomasz Bak
 */
public class EurekaDeployment {

    private final EmbeddedWriteCluster writeCluster;
    private final EmbeddedReadCluster readCluster;
    private final EmbeddedBridgeServer bridgeServer;
    private final EmbeddedDashboardServer dashboardServer;

    public EurekaDeployment(EmbeddedWriteCluster writeCluster,
                            EmbeddedReadCluster readCluster,
                            EmbeddedBridgeServer bridgeServer,
                            EmbeddedDashboardServer dashboardServer) {
        this.writeCluster = writeCluster;
        this.readCluster = readCluster;
        this.bridgeServer = bridgeServer;
        this.dashboardServer = dashboardServer;
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
    }
}
