package com.netflix.eureka2.client;

import com.netflix.eureka2.client.registration.RegistrationObservable;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import rx.Observable;

/**
 * An Eureka client that support registering registrants to remote Eureka servers. Multiple registrants can
 * use the same client for remote server registration.
 *
 * Once registered, the client is responsible for maintaining persisted heartbeating connections with the remote
 * server to maintain the registration until the registrant explicitly unregisters.
 *
 * @author David Liu
 */
public interface EurekaRegistrationClient {

    /**
     * Return a {@link com.netflix.eureka2.client.registration.RegistrationObservable} that when subscribes to, initiates registration with the remote server
     * based on the InstanceInfos received. Changes between InstanceInfos will be applied as updates to the initial
     * registration. InstanceInfo Ids cannot change for InstanceInfos within an input stream.
     *
     * @param registrant an Observable that emits new InstanceInfos for the registrant each time it needs to be
     *                   updated. Initial registrations is predicated on two conditions, the returned
     *                   RegistrationRequest must be subscribed to, and an initial InstanceInfo must be emitted
     *                   by the input observable.
     * @return {@link com.netflix.eureka2.client.registration.RegistrationObservable}
     */
    RegistrationObservable register(Observable<InstanceInfo> registrant);

    /**
     * shutdown and clean up all resources for this client
     */
    void shutdown();

}
