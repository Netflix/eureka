package com.netflix.eureka2.simulator;

import java.util.ArrayList;
import java.util.List;

import com.netflix.discovery.DiscoveryClient;
import org.junit.rules.ExternalResource;

/**
 * @author Tomasz Bak
 */
public class Eureka1ServerResource extends ExternalResource {

    private static final String EUREKA1_SIMULATOR_CLIENT_FILE = "eureka1-simulator-client";

    private Eureka1Server eureka1Server;
    private final List<DiscoveryClient> createdClients = new ArrayList<>();

    @Override
    protected void before() throws Throwable {
        System.setProperty("eureka.client.props", EUREKA1_SIMULATOR_CLIENT_FILE);
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
        } finally {
            System.clearProperty("eureka.client.props");
        }
    }

    public Eureka1Server getServer() {
        return eureka1Server;
    }

    public DiscoveryClient createDiscoveryClient(String appName) {
        DiscoveryClient client = eureka1Server.createDiscoveryClient(appName);
        createdClients.add(client);
        return client;
    }
}
