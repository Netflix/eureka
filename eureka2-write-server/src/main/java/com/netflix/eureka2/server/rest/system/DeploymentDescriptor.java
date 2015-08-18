package com.netflix.eureka2.server.rest.system;

/**
 * @author Tomasz Bak
 */
public class DeploymentDescriptor {

    private final ClusterDescriptor writeCluster;
    private final ClusterDescriptor readCluster;

    public DeploymentDescriptor(ClusterDescriptor writeCluster, ClusterDescriptor readCluster) {
        this.writeCluster = writeCluster;
        this.readCluster = readCluster;
    }

    public ClusterDescriptor getWriteCluster() {
        return writeCluster;
    }

    public ClusterDescriptor getReadCluster() {
        return readCluster;
    }
}
