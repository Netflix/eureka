package com.netflix.eureka.service;

import com.netflix.eureka.registry.InstanceInfo;
import rx.Observable;

/**
 * A {@link ServiceChannel} implementation for registration.
 *
 *
 * The following are the states of a {@link RegistrationChannel}
 *
 * <ul>
 <li><i>Created</i>: Created but no instance registered.</li>
 <li><i>Registered</i>: Instance registered.</li>
 <li><i>Unregistered</i>: Instance unregistered but channel opened and hence can get into Registered state again.</li>
 <li><i>Close</i>: Channel closed. Nothing can be done on this channel anymore.</li>
 </ul>
 *
 * @author Nitesh Kant
 */
public interface RegistrationChannel extends ServiceChannel {

    /**
     * Registers the passed instance with eureka.
     *
     * @param instanceInfo The instance definition.
     *
     * @return Acknowledgment for the registration.
     */
    Observable<Void> register(InstanceInfo instanceInfo);

    /**
     * Updates the {@link InstanceInfo} registered with this channel.
     *
     * @param newInfo The updated info.
     *
     * @return Acknowledgment for this update.
     */
    Observable<Void> update(InstanceInfo newInfo);

    /**
     * Unregisters the {@link InstanceInfo} associated with this channel.
     *
     * @return Acknowledgment for unregistration.
     */
    Observable<Void> unregister();
}
