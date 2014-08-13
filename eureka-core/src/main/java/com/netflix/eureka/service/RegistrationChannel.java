package com.netflix.eureka.service;

import com.netflix.eureka.registry.InstanceInfo;
import rx.Observable;

/**
 * A {@link com.netflix.eureka.service.ServiceChannel} implementation representing registration of an
 * {@link InstanceInfo}.
 *
 * {@link #close()} indicates un-registration of the instance.
 *
 * @author Nitesh Kant
 */
public interface RegistrationChannel extends ServiceChannel {

    /**
     * Updates the {@link InstanceInfo} registered with this channel.
     *
     * @param newInfo The updated info.
     *
     * @return Acknowledgment for this update.
     */
    Observable<Void> update(InstanceInfo newInfo);
}
