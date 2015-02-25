package com.netflix.eureka2.testkit.junit.resources;

import com.netflix.eureka2.client.Eureka;
import com.netflix.eureka2.client.EurekaClient;
import com.netflix.eureka2.client.EurekaClientBuilder;
import com.netflix.eureka2.config.BasicEurekaTransportConfig;
import com.netflix.eureka2.config.EurekaTransportConfig;
import com.netflix.eureka2.testkit.embedded.EurekaDeployment;
import com.netflix.eureka2.testkit.embedded.EurekaDeployment.EurekaDeploymentBuilder;
import com.netflix.eureka2.testkit.embedded.server.EmbeddedReadServer;
import com.netflix.eureka2.testkit.embedded.server.EmbeddedWriteServer;
import org.junit.rules.ExternalResource;

/**
 * @author Tomasz Bak
 */
public class EurekaDeploymentResource extends ExternalResource {

    private final int writeClusterSize;
    private final int readClusterSize;
    private final EurekaTransportConfig transportConfig;

    private EurekaDeployment eurekaDeployment;

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
     * Create {@link EurekaClient} instance connected to a particular write server.
     *
     * @param idx id of a write server where to connect
     */
    public EurekaClient connectToWriteServer(int idx) {
        EmbeddedWriteServer server = eurekaDeployment.getWriteCluster().getServer(idx);
        return EurekaClientBuilder.newBuilder()
                .withReadServerResolver(server.getInterestServerResolver())
                .withWriteServerResolver(server.getRegistrationResolver())
                .withTransportConfig(transportConfig)
                .build();
    }

    /**
     * Create {@link EurekaClient} instance connected to a particular read server (interest subscription only).
     *
     * @param idx id of a read server where to connect
     */
    public EurekaClient connectToReadServer(int idx) {
        EmbeddedReadServer server = eurekaDeployment.getReadCluster().getServer(idx);
        return EurekaClientBuilder.discoveryBuilder()
                .withReadServerResolver(server.getInterestServerResolver())
                .withTransportConfig(transportConfig)
                .build();
    }

    /**
     * Create {@link EurekaClient} instance connected to a write cluster.
     */
    public EurekaClient connectToWriteCluster() {
        return EurekaClientBuilder.newBuilder()
                .withReadServerResolver(eurekaDeployment.getWriteCluster().discoveryResolver())
                .withWriteServerResolver(eurekaDeployment.getWriteCluster().registrationResolver())
                .withTransportConfig(transportConfig)
                .build();
    }

    /**
     * Create {@link EurekaClient} instance connected to a read cluster.
     */
    public EurekaClient connectToReadCluster() {
        return EurekaClientBuilder.discoveryBuilder()
                .withReadServerResolver(eurekaDeployment.getReadCluster().discoveryResolver())
                .withTransportConfig(transportConfig)
                .build();
    }

    /**
     * Create {@link EurekaClient} in canonical setup, where read cluster is discovered from
     * write cluster first.
     */
    public EurekaClient connectToEureka() {
        return Eureka.newClientBuilder(
                eurekaDeployment.getWriteCluster().discoveryResolver(),
                eurekaDeployment.getWriteCluster().registrationResolver(),
                eurekaDeployment.getReadCluster().getVip()
        ).withTransportConfig(transportConfig)
                .build();
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
