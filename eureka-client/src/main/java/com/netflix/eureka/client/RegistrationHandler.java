package com.netflix.eureka.client;

import com.netflix.eureka.registry.InstanceInfo;
import rx.Observable;

/**
 * A contract for the client to submit registrations to the eureka write server.
 *
 * This supports multiple registrations from the same client and transparently handles reconnect and re-registration if
 * the connection to the eureka server is broken, unless the instance was explicitly unregistered.
 *
 * @author Nitesh Kant
 */
public interface RegistrationHandler {

    Observable<Void> register(InstanceInfo instanceInfo);

    Observable<Void> unregister(InstanceInfo instanceInfo);

    Observable<Void> update(InstanceInfo instanceInfo);

    void shutdown();
}
