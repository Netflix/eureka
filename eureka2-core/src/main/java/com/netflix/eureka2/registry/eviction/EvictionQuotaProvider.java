package com.netflix.eureka2.registry.eviction;

import rx.Observable;

/**
 * Back pressure aware observable that emits eviction quotas, when requested
 * by {@link com.netflix.eureka2.registry.PreservableRegistryProcessor}.
 *
 * @author Tomasz Bak
 */
public interface EvictionQuotaProvider {
    Observable<Long> quota();
}
