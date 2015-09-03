package com.netflix.eureka2.channel;

import com.netflix.eureka2.model.instance.InstanceInfo;
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

    enum STATE {Idle, Registered, Closed}

    /**
     * Register or update the passed instance with eureka.
     *
     * @param instanceInfo The instance definition.
     *
     * @return Acknowledgment for the registration.
     */
    Observable<Void> register(InstanceInfo instanceInfo);

    /**
     * Unregisters the {@link InstanceInfo} associated with this channel.
     *
     * @return Acknowledgment for unregistration.
     */
    Observable<Void> unregister();
}
