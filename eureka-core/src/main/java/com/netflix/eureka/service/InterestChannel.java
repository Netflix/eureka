package com.netflix.eureka.service;

import com.netflix.eureka.interests.ChangeNotification;
import com.netflix.eureka.interests.Interest;
import com.netflix.eureka.registry.InstanceInfo;
import rx.Observable;

/**
 * A {@link ServiceChannel} implementation for interest set notifications.
 *
 * The following are the states of an {@link InterestChannel}
 *
 * <ul>
 <li><i>Created</i>: Created but no interest registered.</li>
 <li><i>Registered</i>: Interest registered.</li>
 <li><i>Unregistered</i>: Interest unregistered but channel opened and hence can get into Registered state again.</li>
 <li><i>Close</i>: Channel closed. Nothing can be done on this channel anymore.</li>
 </ul>
 *
 * @author Nitesh Kant
 */
public interface InterestChannel extends ServiceChannel {

    /**
     * Registers an interest on this channel.
     *
     * @param interest Interest to register on this channel.
     *
     * @return Stream of {@link ChangeNotification}
     */
    Observable<ChangeNotification<InstanceInfo>> register(Interest<InstanceInfo> interest);

    /**
     * An operation to upgrade the interest set for this channel. When this upgrade is acknowledged, the older interest
     * set is removed and the passed {@code newInterest} becomes the only interest for this channel. So, any upgrade
     * should include the earlier interest if required.
     *
     * The returned {@link Observable} acts as the acknowledgment for this upgrade.
     *
     * @param newInterest The new interest for this channel.
     *
     * @return An acknowledgment for this upgrade.
     */
    Observable<Void> upgrade(Interest<InstanceInfo> newInterest);

    /**
     * Unregisters any previously registered {@link Interest} on this channel.
     *
     * @return Acknowledgment for the unregistration.
     */
    Observable<Void> unregister();
}
