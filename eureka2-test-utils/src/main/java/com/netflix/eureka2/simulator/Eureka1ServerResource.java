package com.netflix.eureka2.simulator;

import java.util.ArrayList;
import java.util.List;

import com.netflix.discovery.DiscoveryClient;
import org.junit.rules.ExternalResource;

/**
 * @author Tomasz Bak
 */
public class Eureka1ServerResource extends ExternalResource {

    private Eureka1Server eureka1Server;
    private final List<DiscoveryClient> createdClients = new ArrayList<>();

    @Override
    protected void before() throws Throwable {
        eureka1Server = new Eureka1Server();
        eureka1Server.start();
    }

    @Override
    protected void after() {
        for (DiscoveryClient discoveryClient : createdClients) {
            discoveryClient.shutdown();
        }
        try {
            eureka1Server.stop();
        } catch (InterruptedException e) {
            // IGONRE
        }
    }

    public Eureka1Server getServer() {
        return eureka1Server;
    }

    public DiscoveryClient createDiscoveryClient(String appName) {
        return eureka1Server.createDiscoveryClient(appName);
    }
}
