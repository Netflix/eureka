package com.netflix.eureka.client.registration;

import com.netflix.eureka.protocol.registration.Register;
import com.netflix.eureka.protocol.registration.Update;
import rx.Observable;

/**
 * Client registration protocol.
 * Heartbeats are not relevant for application layer so are handled underneath.
 *
 * @author Tomasz Bak
 */
public interface RegistrationClient {

    Observable<Void> register(Register registryInfo);

    Observable<Void> update(Update update);

    Observable<Void> unregister();

    void shutdown();

    /**
     * Application must subscribe to this to know when communication channel is broken, and client
     * must reconnect. Failed heartbeats will be visible only via this observable.
     */
    Observable<Void> lifecycleObservable();
}
