package com.netflix.eureka2.registry;

import com.netflix.eureka2.EurekaCloseable;
import rx.Observable;

/**
 * @author Tomasz Bak
 */
public interface EurekaRegistrationProcessor<T> extends EurekaCloseable {

    Observable<Void> register(String id, Observable<T> registrationUpdates, Source source);

    /**
     * @return a boolean to denote whether the register added a new entry or updated an existing entry
     */
    Observable<Boolean> register(T registrant, Source source);

    /**
     * @return a boolean to denote whether the unregister removed an existing entry
     */
    Observable<Boolean> unregister(T registrant, Source source);
}
