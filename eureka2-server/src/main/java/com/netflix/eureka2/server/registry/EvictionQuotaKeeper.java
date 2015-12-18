package com.netflix.eureka2.server.registry;

import rx.Observable;

/**
 * Back pressure aware observable that emits eviction quotas, when requested
 * by {@link PreservableRegistrationProcessor}.
 *
 * @author Tomasz Bak
 */
public interface EvictionQuotaKeeper {

    Observable<Long> quota();
}
