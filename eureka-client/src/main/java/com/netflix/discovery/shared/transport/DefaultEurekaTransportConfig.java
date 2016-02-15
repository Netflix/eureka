package com.netflix.discovery.shared.transport;

import com.netflix.config.DynamicPropertyFactory;

/**
 * @author David Liu
 */
public class DefaultEurekaTransportConfig implements EurekaTransportConfig {
    private static final String SUB_NAMESPACE = "transport.";

    private final String namespace;
    private final DynamicPropertyFactory configInstance;

    public DefaultEurekaTransportConfig(String parentNamespace, DynamicPropertyFactory configInstance) {
        this.namespace = parentNamespace == null
                ? SUB_NAMESPACE
                : parentNamespace + SUB_NAMESPACE;
        this.configInstance = configInstance;
    }

    @Override
    public int getSessionedClientReconnectIntervalSeconds() {
        return configInstance.getIntProperty(namespace + "sessionedClientReconnectIntervalSeconds", 20*60).get();
    }

    @Override
    public double getRetryableClientQuarantineRefreshPercentage() {
        return configInstance.getDoubleProperty(namespace + "retryableClientQuarantineRefreshPercentage", 0.66).get();
    }

    @Override
    public int getApplicationsResolverDataStalenessThresholdSeconds() {
        return configInstance.getIntProperty(namespace + "applicationsResolverDataStalenessThresholdSeconds", 5*60).get();
    }

    @Override
    public boolean applicationsResolverUseIp() {
        return configInstance.getBooleanProperty(namespace + "applicationsResolverUseIp", false).get();
    }

    @Override
    public int getAsyncResolverRefreshIntervalMs() {
        return configInstance.getIntProperty(namespace + "asyncResolverRefreshIntervalMs", 5*60*1000).get();
    }

    @Override
    public int getAsyncResolverWarmUpTimeoutMs() {
        return configInstance.getIntProperty(namespace + "asyncResolverWarmupTimeoutMs", 5000).get();
    }

    @Override
    public int getAsyncExecutorThreadPoolSize() {
        return configInstance.getIntProperty(namespace + "asyncExecutorThreadPoolSize", 5).get();
    }

    @Override
    public String getWriteClusterVip() {
        return configInstance.getStringProperty(namespace + "writeClusterVip", null).get();
    }

    @Override
    public String getReadClusterVip() {
        return configInstance.getStringProperty(namespace + "readClusterVip", null).get();
    }

    @Override
    public String getBootstrapResolverStrategy() {
        return configInstance.getStringProperty(namespace + "bootstrapResolverStrategy", null).get();
    }

    @Override
    public boolean useBootstrapResolverForQuery() {
        return configInstance.getBooleanProperty(namespace + "useBootstrapResolverForQuery", true).get();
    }
}
