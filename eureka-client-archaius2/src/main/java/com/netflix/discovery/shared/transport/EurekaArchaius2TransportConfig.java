package com.netflix.discovery.shared.transport;

import com.google.inject.Inject;
import com.netflix.archaius.api.Config;
import com.netflix.archaius.api.annotations.ConfigurationSource;
import com.netflix.discovery.CommonConstants;
import com.netflix.discovery.internal.util.InternalPrefixedConfig;

import javax.inject.Singleton;

import static com.netflix.discovery.shared.transport.PropertyBasedTransportConfigConstants.*;

/**
 * @author David Liu
 */
@Singleton
@ConfigurationSource(CommonConstants.CONFIG_FILE_NAME)
public class EurekaArchaius2TransportConfig implements EurekaTransportConfig {
    private final Config configInstance;
    private final InternalPrefixedConfig prefixedConfig;

    @Inject
    public EurekaArchaius2TransportConfig(Config configInstance) {
        this(configInstance, CommonConstants.DEFAULT_CONFIG_NAMESPACE, TRANSPORT_CONFIG_SUB_NAMESPACE);
    }

    public EurekaArchaius2TransportConfig(Config configInstance, String parentNamespace) {
        this(configInstance, parentNamespace, TRANSPORT_CONFIG_SUB_NAMESPACE);
    }

    public EurekaArchaius2TransportConfig(Config configInstance, String parentNamespace, String subNamespace) {
        this.configInstance = configInstance;
        this.prefixedConfig = new InternalPrefixedConfig(configInstance, parentNamespace, subNamespace);
    }

    @Override
    public int getSessionedClientReconnectIntervalSeconds() {
        return prefixedConfig.getInteger(SESSION_RECONNECT_INTERVAL_KEY, Values.SESSION_RECONNECT_INTERVAL);
    }

    @Override
    public double getRetryableClientQuarantineRefreshPercentage() {
        return prefixedConfig.getDouble(QUARANTINE_REFRESH_PERCENTAGE_KEY, Values.QUARANTINE_REFRESH_PERCENTAGE);
    }

    @Override
    public int getApplicationsResolverDataStalenessThresholdSeconds() {
        return prefixedConfig.getInteger(DATA_STALENESS_THRESHOLD_KEY, Values.DATA_STALENESS_TRHESHOLD);
    }

    @Override
    public boolean applicationsResolverUseIp() {
        return prefixedConfig.getBoolean(APPLICATION_RESOLVER_USE_IP_KEY, false);
    }

    @Override
    public int getAsyncResolverRefreshIntervalMs() {
        return prefixedConfig.getInteger(ASYNC_RESOLVER_REFRESH_INTERVAL_KEY, Values.ASYNC_RESOLVER_REFRESH_INTERVAL);
    }

    @Override
    public int getAsyncResolverWarmUpTimeoutMs() {
        return prefixedConfig.getInteger(ASYNC_RESOLVER_WARMUP_TIMEOUT_KEY, Values.ASYNC_RESOLVER_WARMUP_TIMEOUT);
    }

    @Override
    public int getAsyncExecutorThreadPoolSize() {
        return prefixedConfig.getInteger(ASYNC_EXECUTOR_THREADPOOL_SIZE_KEY, Values.ASYNC_EXECUTOR_THREADPOOL_SIZE);
    }

    @Override
    public String getWriteClusterVip() {
        return prefixedConfig.getString(WRITE_CLUSTER_VIP_KEY, null);
    }

    @Override
    public String getReadClusterVip() {
        return prefixedConfig.getString(READ_CLUSTER_VIP_KEY, null);
    }

    @Override
    public String getBootstrapResolverStrategy() {
        return prefixedConfig.getString(BOOTSTRAP_RESOLVER_STRATEGY_KEY, null);
    }

    @Override
    public boolean useBootstrapResolverForQuery() {
        return prefixedConfig.getBoolean(USE_BOOTSTRAP_RESOLVER_FOR_QUERY, true);
    }
}
