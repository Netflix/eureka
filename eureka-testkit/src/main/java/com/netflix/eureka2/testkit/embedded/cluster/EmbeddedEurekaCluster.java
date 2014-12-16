package com.netflix.eureka2.testkit.embedded.cluster;

import java.util.ArrayList;
import java.util.List;

import com.netflix.eureka2.registry.InstanceInfo;
import com.netflix.eureka2.server.registry.EurekaServerRegistry;
import com.netflix.eureka2.testkit.embedded.server.EmbeddedEurekaServer;

/**
 * @author Tomasz Bak
 */
public abstract class EmbeddedEurekaCluster<S extends EmbeddedEurekaServer, R> {

    private final String clusterVip;

    protected final List<S> servers = new ArrayList<>();

    protected EmbeddedEurekaCluster(String clusterVip) {
        this.clusterVip = clusterVip;
    }

    public abstract int scaleUpByOne();

    public int scaleUpBy(int count) {
        for (int i = 0; i < count; i++) {
            scaleUpByOne();
        }
        return servers.size() - 1;
    }

    public void scaleDownByOne() {
        scaleDownByOne(servers.size() - 1);
    }

    public void scaleDownByOne(int idx) {
        S server = servers.remove(idx);
        server.shutdown();
    }

    public void scaleDownBy(int count) {
        for (int i = 0; i < count; i++) {
            scaleDownByOne();
        }
    }

    public void startUp(int idx) {
        S server = servers.get(idx);
        if (server != null) {
            server.start();
        }
    }

    public void bringDown(int idx) {
        S server = servers.get(idx);
        if (server != null) {
            server.shutdown();
        }
    }

    public void shutdown() {
        for (S server : servers) {
            server.shutdown();
        }
    }

    public String getVip() {
        return clusterVip;
    }

    public EurekaServerRegistry<InstanceInfo> getEurekaServerRegistry(int idx) {
        return servers.get(idx).getEurekaServerRegistry();
    }

    public abstract R clusterReport();
}
