package com.netflix.eureka.service;

import com.netflix.eureka.datastore.Item;
import com.netflix.eureka.interests.ChangeNotification;
import com.netflix.eureka.interests.Interest;
import com.netflix.eureka.registry.EurekaRegistry;
import com.netflix.eureka.registry.InstanceInfo;
import rx.Observable;

/**
 * A {@link ServiceChannel} implementation representing an {@link Interest} for changes to {@link EurekaRegistry}
 *
 * @author Nitesh Kant
 */
public interface InterestChannel extends ServiceChannel {

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
     * Returns the stream of {@link ChangeNotification}s for this channel.
     *
     * @return The stream of {@link ChangeNotification}s for this channel.
     */
    Observable<ChangeNotification<? extends Item>> asObservable();
}
