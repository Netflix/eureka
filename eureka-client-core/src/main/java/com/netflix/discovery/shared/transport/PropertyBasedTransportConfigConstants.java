package com.netflix.discovery.shared.transport;

/**
 * constants pertaining to property based transport configs
 *
 * @author David Liu
 */
final class PropertyBasedTransportConfigConstants {

    // NOTE: all keys are before any prefixes are applied
    static final String SESSION_RECONNECT_INTERVAL_KEY = "sessionedClientReconnectIntervalSeconds";
    static final String QUARANTINE_REFRESH_PERCENTAGE_KEY = "retryableClientQuarantineRefreshPercentage";
    static final String DATA_STALENESS_THRESHOLD_KEY = "applicationsResolverDataStalenessThresholdSeconds";
    static final String APPLICATION_RESOLVER_USE_IP_KEY = "applicationsResolverUseIp";
    static final String ASYNC_RESOLVER_REFRESH_INTERVAL_KEY = "asyncResolverRefreshIntervalMs";
    static final String ASYNC_RESOLVER_WARMUP_TIMEOUT_KEY = "asyncResolverWarmupTimeoutMs";
    static final String ASYNC_EXECUTOR_THREADPOOL_SIZE_KEY = "asyncExecutorThreadPoolSize";
    static final String WRITE_CLUSTER_VIP_KEY = "writeClusterVip";
    static final String READ_CLUSTER_VIP_KEY = "readClusterVip";
    static final String BOOTSTRAP_RESOLVER_STRATEGY_KEY = "bootstrapResolverStrategy";
    static final String USE_BOOTSTRAP_RESOLVER_FOR_QUERY = "useBootstrapResolverForQuery";

    static final String TRANSPORT_CONFIG_SUB_NAMESPACE = "transport";


    static class Values {
        static final int SESSION_RECONNECT_INTERVAL = 20*60;
        static final double QUARANTINE_REFRESH_PERCENTAGE = 0.66;
        static final int DATA_STALENESS_TRHESHOLD = 5*60;
        static final int ASYNC_RESOLVER_REFRESH_INTERVAL = 5*60*1000;
        static final int ASYNC_RESOLVER_WARMUP_TIMEOUT = 5000;
        static final int ASYNC_EXECUTOR_THREADPOOL_SIZE = 5;
    }
}
