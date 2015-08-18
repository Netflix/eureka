package com.netflix.eureka2.server.rest.system;

import java.util.List;

import com.netflix.eureka2.registry.instance.InstanceInfo;

/**
 * @author Tomasz Bak
 */
public class ClusterDescriptor {

    private final String clusterId;
    private final List<InstanceInfo> servers;

    public ClusterDescriptor(String clusterId, List<InstanceInfo> servers) {
        this.clusterId = clusterId;
        this.servers = servers;
    }

    public String getClusterId() {
        return clusterId;
    }

    public List<InstanceInfo> getServers() {
        return servers;
    }
}
