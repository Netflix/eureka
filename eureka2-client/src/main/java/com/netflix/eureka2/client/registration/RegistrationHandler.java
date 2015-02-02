package com.netflix.eureka2.client.registration;

import com.netflix.eureka2.registry.instance.InstanceInfo;
import rx.Observable;

/**
 * @author David Liu
 */
public interface RegistrationHandler {

    RegistrationResponse register(Observable<InstanceInfo> lifecycle);

    void shutdown();
}
