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
        return configInstance.getIntProperty(namespace + "sessionedClientReconnectIntervalSeconds", 30*60).get();
    }

    @Override
    public int getBootstrapResolverRefreshIntervalSeconds() {
        return configInstance.getIntProperty(namespace + "bootstrapResolverRefreshIntervalSeconds", 5*60).get();
    }

    @Override
    public int getAsyncResolverRefreshIntervalSeconds() {
        return configInstance.getIntProperty(namespace + "asyncResolverRefreshIntervalSeconds", 5*60).get();
    }

    @Override
    public int getAsyncExecutorThreadPoolSize() {
        return configInstance.getIntProperty(namespace + "asyncExecutorThreadPoolSize", 5).get();
    }

    @Override
    public String getReadClusterVip() {
        return configInstance.getStringProperty(namespace + "readClusterVip", null).get();
    }
}
