package com.netflix.eureka.service;

import com.netflix.eureka.interests.Interest;
import com.netflix.eureka.registry.InstanceInfo;
import rx.Observable;

/**
 * A {@link ServiceChannel} implementation for interest set notifications.
 * The channel interest can be upgraded any number of times as long as the channel is open.
 *
 * The following are the states of an {@link InterestChannel}
 *
 * <ul>
 <li><i>Idle</i>: Channel is created but no streams are flowing</li>
 <li><i>Open</i>: Channel is open and interest stream is flowing (can be empty stream due to empty interest)</li>
 <li><i>Close</i>: Channel closed. Nothing can be done on this channel anymore.</li>
 </ul>
 *
 * @author Nitesh Kant
 */
public interface InterestChannel extends ServiceChannel {
    /**
     * An operation to change the interest set for this channel to a new interest set.
     * When this change is acknowledged, the older interest set is removed and the passed
     * {@code newInterest} becomes the only interest for this channel. So, any change
     * interest set should include the earlier interest if required.
     *
     * The returned {@link Observable} acts as the acknowledgment for this change.
     *
     * @param newInterest The new interest for this channel.
     *
     * @return An acknowledgment for this change.
     */
    Observable<Void> change(Interest<InstanceInfo> newInterest);
}
