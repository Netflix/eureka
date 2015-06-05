package com.netflix.eureka2.registry;

import com.netflix.eureka2.EurekaCloseable;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import rx.Observable;

/**
 * @author Tomasz Bak
 */
public interface EurekaRegistrationProcessor<T> extends EurekaCloseable {

    /**
     */
    Observable<Void> register(String id, Source source, Observable<InstanceInfo> registrationUpdates);

    /**
     * @return a boolean to denote whether the register added a new entry or updated an existing entry
     */
    Observable<Boolean> register(T instanceInfo, Source source);

    /**
     * @return a boolean to denote whether the unregister removed an existing entry
     */
    Observable<Boolean> unregister(T instanceInfo, Source source);
}
