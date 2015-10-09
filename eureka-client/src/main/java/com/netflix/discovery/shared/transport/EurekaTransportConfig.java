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
     * @return the interval to poll for the async resolver.
     */
    int getAsyncResolverRefreshIntervalSeconds();

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


}
