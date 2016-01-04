package com.netflix.eureka2.testkit.junit.resources;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.inject.Module;
import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.EurekaRegistrationClient;
import com.netflix.eureka2.server.config.EurekaServerTransportConfig;
import com.netflix.eureka2.server.spi.ExtAbstractModule.ServerType;
import com.netflix.eureka2.testkit.embedded.EurekaDeployment;
import com.netflix.eureka2.testkit.embedded.EurekaDeployment.EurekaDeploymentBuilder;
import com.netflix.eureka2.testkit.junit.resources.EurekaExternalResources.EurekaExternalResource;

import static com.netflix.eureka2.server.config.bean.EurekaServerTransportConfigBean.anEurekaServerTransportConfig;

/**
 * @author Tomasz Bak
 */
public class EurekaDeploymentResource extends EurekaExternalResource {

    private final int writeClusterSize;
    private final int readClusterSize;
    private final EurekaServerTransportConfig transportConfig;
    private final boolean networkRouterEnabled;
    private final boolean extensionsEnabled;
    private final Map<ServerType, List<Class<? extends Module>>> extensionModules;
    private final Map<ServerType, Map<Class<?>, Object>> configurationOverrides;

    private EurekaDeployment eurekaDeployment;

    /**
     * Use builder instead {@link EurekaDeploymentResourceBuilder}.
     */
    @Deprecated
    public EurekaDeploymentResource(int writeClusterSize, int readClusterSize) {
        this(writeClusterSize, readClusterSize, anEurekaServerTransportConfig().build());
    }

    /**
     * Use builder instead {@link EurekaDeploymentResourceBuilder}.
     */
    @Deprecated
    public EurekaDeploymentResource(int writeClusterSize, int readClusterSize, EurekaServerTransportConfig transportConfig) {
        this.writeClusterSize = writeClusterSize;
        this.readClusterSize = readClusterSize;
        this.transportConfig = transportConfig;
        this.networkRouterEnabled = false;
        this.extensionsEnabled = false;
        this.extensionModules = null;
        this.configurationOverrides = null;
    }

    private EurekaDeploymentResource(EurekaDeploymentResourceBuilder builder) {
        this.writeClusterSize = builder.writeClusterSize;
        this.readClusterSize = builder.readClusterSize;
        this.transportConfig = builder.transportConfig;
        this.networkRouterEnabled = builder.networkRouterEnabled;
        this.extensionsEnabled = builder.includeExtensions;
        this.extensionModules = builder.extensionModules;
        this.configurationOverrides = builder.configurationOverrides;
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

    /**
     * Create a {@link EurekaInterestClient} instance to do interest discovery with any instance in a read cluster,
     * using the canonical method to first discover the read cluster from the write cluster
     */
    public EurekaInterestClient cannonicalInterestClient(String clientName) {
        return eurekaDeployment.cannonicalInterestClient(clientName);
    }

    @Override
    protected void before() throws Throwable {
        eurekaDeployment = new EurekaDeploymentBuilder()
                .withWriteClusterSize(writeClusterSize)
                .withReadClusterSize(readClusterSize)
                .withEphemeralPorts(true)
                .withTransportConfig(transportConfig)
                .withNetworkRouter(networkRouterEnabled)
                .withExtensions(extensionsEnabled)
                .withConfiguration(configurationOverrides)
                .withExtensionModules(extensionModules)
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

        private EurekaServerTransportConfig transportConfig;
        private boolean networkRouterEnabled;
        private boolean includeExtensions;

        private final Map<ServerType, List<Class<? extends Module>>> extensionModules = new EnumMap<ServerType, List<Class<? extends Module>>>(ServerType.class);
        private final Map<ServerType, Map<Class<?>, Object>> configurationOverrides = new EnumMap<ServerType, Map<Class<?>, Object>>(ServerType.class);

        private EurekaDeploymentResourceBuilder(int writeClusterSize, int readClusterSize) {
            this.writeClusterSize = writeClusterSize;
            this.readClusterSize = readClusterSize;
        }

        public EurekaDeploymentResourceBuilder withNetworkRouter(boolean networkRouterEnabled) {
            this.networkRouterEnabled = networkRouterEnabled;
            return this;
        }

        public EurekaDeploymentResourceBuilder withExtensions(boolean includeExtensions) {
            this.includeExtensions = includeExtensions;
            return this;
        }

        public EurekaDeploymentResourceBuilder withTransportConfig(EurekaServerTransportConfig transportConfig) {
            this.transportConfig = transportConfig;
            return this;
        }

        public EurekaDeploymentResourceBuilder withExtensionModule(ServerType serverType, Class<? extends Module> extensionModule) {
            List<Class<? extends Module>> modules = extensionModules.get(serverType);
            if (modules == null) {
                modules = new ArrayList<>();
                extensionModules.put(serverType, modules);
            }
            modules.add(extensionModule);
            return this;
        }

        public EurekaDeploymentResourceBuilder withConfiguration(ServerType serverType, Class<?> type, Object configuration) {
            Map<Class<?>, Object> configurations = configurationOverrides.get(serverType);
            if (configurations == null) {
                configurations = new HashMap<>();
                configurationOverrides.put(serverType, configurations);
            }
            configurations.put(type, configuration);
            return this;
        }

        public EurekaDeploymentResource build() {
            return new EurekaDeploymentResource(this);
        }
    }
}
