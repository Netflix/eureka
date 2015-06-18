package com.netflix.eureka2.testkit.junit.resources;

import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.EurekaRegistrationClient;
import com.netflix.eureka2.config.BasicEurekaTransportConfig;
import com.netflix.eureka2.config.EurekaTransportConfig;
import com.netflix.eureka2.testkit.embedded.EurekaDeployment;
import com.netflix.eureka2.testkit.embedded.EurekaDeployment.EurekaDeploymentBuilder;
import com.netflix.eureka2.testkit.junit.resources.EurekaExternalResources.EurekaExternalResource;

/**
 * @author Tomasz Bak
 */
public class EurekaDeploymentResource extends EurekaExternalResource {

    private final int writeClusterSize;
    private final int readClusterSize;
    private final EurekaTransportConfig transportConfig;
    private final boolean networkRouterEnabled;

    private EurekaDeployment eurekaDeployment;

    /**
     * Use builder instead {@link EurekaDeploymentResourceBuilder}.
     */
    @Deprecated
    public EurekaDeploymentResource(int writeClusterSize, int readClusterSize) {
        this(writeClusterSize, readClusterSize, new BasicEurekaTransportConfig.Builder().build());
    }

    /**
     * Use builder instead {@link EurekaDeploymentResourceBuilder}.
     */
    @Deprecated
    public EurekaDeploymentResource(int writeClusterSize, int readClusterSize, EurekaTransportConfig transportConfig) {
        this.writeClusterSize = writeClusterSize;
        this.readClusterSize = readClusterSize;
        this.transportConfig = transportConfig;
        this.networkRouterEnabled = false;
    }

    private EurekaDeploymentResource(EurekaDeploymentResourceBuilder builder) {
        this.writeClusterSize = builder.writeClusterSize;
        this.readClusterSize = builder.readClusterSize;
        this.transportConfig = builder.transportConfig;
        this.networkRouterEnabled = builder.networkRouterEnabled;
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
        return eurekaDeployment.registrationClientToWriteServer(idx);
    }

    /**
     * Create a {@link EurekaRegistrationClient} instance to register with any instance in a write cluster
     */
    public EurekaRegistrationClient registrationClientToWriteCluster() {
        return eurekaDeployment.registrationClientToWriteCluster();
    }

    /**
     * Create a {@link EurekaInterestClient} instance to do interest discovery with a particular write server
     *
     * @param idx id of a write server where to connect
     */
    public EurekaInterestClient interestClientToWriteServer(int idx) {
        return eurekaDeployment.interestClientToWriteServer(idx);
    }

    /**
     * Create a {@link EurekaInterestClient} instance to do interest discovery with any instance in a write cluster
     */
    public EurekaInterestClient interestClientToWriteCluster() {
        return eurekaDeployment.interestClientToWriteCluster();
    }

    /**
     * Create a {@link EurekaInterestClient} instance to do interest discovery with a particular read server
     *
     * @param idx id of a write server where to connect
     */
    public EurekaInterestClient interestClientToReadServer(int idx) {
        return eurekaDeployment.interestClientToReadServer(idx);
    }

    /**
     * Create a {@link EurekaInterestClient} instance to do interest discovery with any instance in a read cluster
     */
    public EurekaInterestClient interestClientToReadCluster() {
        return eurekaDeployment.interestClientToReadCluster();
    }

    /**
     * Create a {@link EurekaInterestClient} instance to do interest discovery with any instance in a read cluster,
     * using the canonical method to first discover the read cluster from the write cluster
     */
    public EurekaInterestClient cannonicalInterestClient() {
        return eurekaDeployment.cannonicalInterestClient();
    }

    @Override
    protected void before() throws Throwable {
        eurekaDeployment = new EurekaDeploymentBuilder()
                .withWriteClusterSize(writeClusterSize)
                .withReadClusterSize(readClusterSize)
                .withEphemeralPorts(true)
                .withTransportConfig(transportConfig)
                .withNetworkRouter(networkRouterEnabled)
                .build();
    }

    @Override
    protected void after() {
        if (eurekaDeployment != null) {
            eurekaDeployment.shutdown();
        }
    }

    public static EurekaDeploymentResourceBuilder anEurekaDeploymentResource(int writeClusterSize, int readClusterSize) {
        return new EurekaDeploymentResourceBuilder(writeClusterSize, readClusterSize);
    }

    public static class EurekaDeploymentResourceBuilder {

        private final int writeClusterSize;
        private final int readClusterSize;

        private EurekaTransportConfig transportConfig;
        private boolean networkRouterEnabled;

        public EurekaDeploymentResourceBuilder(int writeClusterSize, int readClusterSize) {
            this.writeClusterSize = writeClusterSize;
            this.readClusterSize = readClusterSize;
        }

        public EurekaDeploymentResourceBuilder withNetworkRouter(boolean networkRouterEnabled) {
            this.networkRouterEnabled = networkRouterEnabled;
            return this;
        }

        public EurekaDeploymentResourceBuilder withTransportConfig(EurekaTransportConfig transportConfig) {
            this.transportConfig = transportConfig;
            return this;
        }

        public EurekaDeploymentResource build() {
            return new EurekaDeploymentResource(this);
        }
    }
}
