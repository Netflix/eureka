package com.netflix.discovery.shared.transport;

import com.google.inject.Inject;
import com.netflix.archaius.api.Config;
import com.netflix.archaius.api.annotations.ConfigurationSource;
import com.netflix.discovery.CommonConstants;

import javax.inject.Singleton;

import static com.netflix.discovery.shared.transport.PropertyBasedTransportConfigConstants.*;

/**
 * @author David Liu
 */
@Singleton
@ConfigurationSource(CommonConstants.CONFIG_FILE_NAME)
public class EurekaArchaius2TransportConfig implements EurekaTransportConfig {
    private final String namespace;
    private final Config config;

    @Inject
    public EurekaArchaius2TransportConfig(Config config) {
        this(config, CommonConstants.DEFAULT_CONFIG_NAMESPACE, TRANSPORT_CONFIG_SUB_NAMESPACE);
    }

    public EurekaArchaius2TransportConfig(Config config, String parentNamespace) {
        this(config, parentNamespace, TRANSPORT_CONFIG_SUB_NAMESPACE);
    }

    public EurekaArchaius2TransportConfig(Config config, String parentNamespace, String subNamespace) {
        this.namespace = parentNamespace + "." + subNamespace;
        this.config = config.getPrefixedView(namespace);
    }

    @Override
    public int getSessionedClientReconnectIntervalSeconds() {
        return config.getInteger(SESSION_RECONNECT_INTERVAL_KEY, Values.SESSION_RECONNECT_INTERVAL);
    }

    @Override
    public double getRetryableClientQuarantineRefreshPercentage() {
        return config.getDouble(QUARANTINE_REFRESH_PERCENTAGE_KEY, Values.QUARANTINE_REFRESH_PERCENTAGE);
    }

    @Override
    public int getApplicationsResolverDataStalenessThresholdSeconds() {
        return config.getInteger(DATA_STALENESS_THRESHOLD_KEY, Values.DATA_STALENESS_TRHESHOLD);
    }

    @Override
    public boolean applicationsResolverUseIp() {
        return config.getBoolean(APPLICATION_RESOLVER_USE_IP_KEY, false);
    }

    @Override
    public int getAsyncResolverRefreshIntervalMs() {
        return config.getInteger(ASYNC_RESOLVER_REFRESH_INTERVAL_KEY, Values.ASYNC_RESOLVER_REFRESH_INTERVAL);
    }

    @Override
    public int getAsyncResolverWarmUpTimeoutMs() {
        return config.getInteger(ASYNC_RESOLVER_WARMUP_TIMEOUT_KEY, Values.ASYNC_RESOLVER_WARMUP_TIMEOUT);
    }

    @Override
    public int getAsyncExecutorThreadPoolSize() {
        return config.getInteger(ASYNC_EXECUTOR_THREADPOOL_SIZE_KEY, Values.ASYNC_EXECUTOR_THREADPOOL_SIZE);
    }

    @Override
    public String getWriteClusterVip() {
        return config.getString(WRITE_CLUSTER_VIP_KEY, null);
    }

    @Override
    public String getReadClusterVip() {
        return config.getString(READ_CLUSTER_VIP_KEY, null);
    }

    @Override
    public String getBootstrapResolverStrategy() {
        return config.getString(BOOTSTRAP_RESOLVER_STRATEGY_KEY, null);
    }

    @Override
    public boolean useBootstrapResolverForQuery() {
        return config.getBoolean(USE_BOOTSTRAP_RESOLVER_FOR_QUERY, true);
    }
}
