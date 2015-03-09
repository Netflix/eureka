package com.netflix.eureka2.testkit.junit.resources;

import java.util.ArrayList;
import java.util.List;

import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.EurekaInterestClientBuilder;
import com.netflix.eureka2.client.EurekaRegistrationClient;
import com.netflix.eureka2.client.EurekaRegistrationClientBuilder;
import com.netflix.eureka2.client.resolver.ServerResolvers;
import com.netflix.eureka2.config.BasicEurekaTransportConfig;
import com.netflix.eureka2.config.EurekaTransportConfig;
import com.netflix.eureka2.testkit.embedded.EurekaDeployment;
import com.netflix.eureka2.testkit.embedded.EurekaDeployment.EurekaDeploymentBuilder;
import com.netflix.eureka2.testkit.embedded.server.EmbeddedReadServer;
import com.netflix.eureka2.testkit.embedded.server.EmbeddedWriteServer;
import com.netflix.eureka2.testkit.junit.resources.EurekaExternalResources.EurekaExternalResource;

import static com.netflix.eureka2.interests.Interests.*;

/**
 * @author Tomasz Bak
 */
public class EurekaDeploymentResource extends EurekaExternalResource {

    private final int writeClusterSize;
    private final int readClusterSize;
    private final EurekaTransportConfig transportConfig;

    private EurekaDeployment eurekaDeployment;

    private final List<EurekaInterestClient> connectedInterestClients = new ArrayList<>();
    private final List<EurekaRegistrationClient> connectedRegistrationClients = new ArrayList<>();

    public EurekaDeploymentResource(int writeClusterSize, int readClusterSize) {
        this(writeClusterSize, readClusterSize, new BasicEurekaTransportConfig.Builder().build());
    }

    public EurekaDeploymentResource(int writeClusterSize, int readClusterSize, EurekaTransportConfig transportConfig) {
        this.writeClusterSize = writeClusterSize;
        this.readClusterSize = readClusterSize;
        this.transportConfig = transportConfig;
    }

    public EurekaDeployment getEurekaDeployment() {
        return eurekaDeployment;
    }

    /**
     * Create a {@link EurekaRegistrationClient} instance to register with a particular write server
     *
     * @param idx id of a write server where to connect
     */
    public EurekaRegistrationClient registrationClientToWriteServer(int idx) {
        EmbeddedWriteServer server = eurekaDeployment.getWriteCluster().getServer(idx);
        EurekaRegistrationClient registrationClient = new EurekaRegistrationClientBuilder()
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
        EurekaRegistrationClient registrationClient = new EurekaRegistrationClientBuilder()
                .withTransportConfig(transportConfig)
                .withServerResolver(eurekaDeployment.getWriteCluster().registrationResolver())
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
        EmbeddedWriteServer server = eurekaDeployment.getWriteCluster().getServer(idx);
        EurekaInterestClient interestClient = new EurekaInterestClientBuilder()
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
        EurekaInterestClient interestClient = new EurekaInterestClientBuilder()
                .withTransportConfig(transportConfig)
                .withServerResolver(eurekaDeployment.getWriteCluster().interestResolver())
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
        EmbeddedReadServer server = eurekaDeployment.getReadCluster().getServer(idx);
        EurekaInterestClient interestClient = new EurekaInterestClientBuilder()
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
        EurekaInterestClient interestClient = new EurekaInterestClientBuilder()
                .withTransportConfig(transportConfig)
                .withServerResolver(eurekaDeployment.getReadCluster().interestResolver())
                .build();
        connectedInterestClients.add(interestClient);
        return interestClient;
    }

    /**
     * Create a {@link EurekaInterestClient} instance to do interest discovery with any instance in a read cluster,
     * using the canonical method to first discover the read cluster from the write cluster
     */
    public EurekaInterestClient cannonicalInterestClient() {
        EurekaInterestClient interestClient = new EurekaInterestClientBuilder()
                .withTransportConfig(transportConfig)
                .withServerResolver(ServerResolvers.fromEureka(eurekaDeployment.getWriteCluster().interestResolver())
                        .forInterest(forVips(eurekaDeployment.getReadCluster().getVip())))
                .build();
        connectedInterestClients.add(interestClient);
        return interestClient;
    }

    @Override
    protected void before() throws Throwable {
        eurekaDeployment = new EurekaDeploymentBuilder()
                .withWriteClusterSize(writeClusterSize)
                .withReadClusterSize(readClusterSize)
                .withEphemeralPorts(true)
                .withTransportConfig(transportConfig)
                .build();
    }

    @Override
    protected void after() {
        if (eurekaDeployment != null) {
            eurekaDeployment.shutdown();
        }
    }
}
