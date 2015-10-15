package com.netflix.discovery.shared.transport;

/**
 * @author David Liu
 */
public interface EurekaTransportConfig {

    /**
     * @return the reconnect inverval to use for sessioned clients
     */
    int getSessionedClientReconnectIntervalSeconds();

    /**
     * Indicates how often(in seconds) to poll for changes to the bootstrap eureka server urls
     *
     * @return the interval to poll for bootstrap eureka server url changes (e.g. if stored in dns)
     */
    int getBootstrapResolverRefreshIntervalSeconds();

    /**
     * @return the max staleness threshold tolerated by the applications resolver
     */
    int getApplicationsResolverDataStalenessThresholdSeconds();

    /**
     * @return the interval to poll for the async resolver.
     */
    int getAsyncResolverRefreshIntervalMs();

    /**
     * @return the async refresh timeout threshold in ms.
     */
    int getAsyncResolverWarmUpTimeoutMs();

    /**
     * @return the max threadpool size for the async resolver's executor
     */
    int getAsyncExecutorThreadPoolSize();

    /**
     * The remote vipAddress of the eureka cluster (either the primaries or a readonly replica) to fetch registry
     * data from.
     *
     * @return the vipAddress for the readonly cluster to redirect to, if applicable (can be the same as the bootstrap)
     */
    String getReadClusterVip();

    /**
     * By default, the new transport uses an indirect resolver to resolve query targets
     * via {@link #getReadClusterVip()}. This indirect resolver may or may not return the same
     * targets as the bootstrap servers depending on how servers are setup.
     *
     * Set this property to true to not use the indirect resolver and only use the server list
     * returned by the bootstrap resolver.
     *
     * @return false by default. true if the server list from the bootstrap resolver should be
     *         used for query instead of the default
     */
    boolean useBootstrapResolverForQuery();
}
