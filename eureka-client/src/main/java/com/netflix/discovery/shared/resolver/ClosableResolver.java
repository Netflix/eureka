package com.netflix.discovery.shared.resolver;

/**
 * @author David Liu
 */
public interface ClosableResolver<T extends EurekaEndpoint> extends ClusterResolver<T> {
    void shutdown();
}
