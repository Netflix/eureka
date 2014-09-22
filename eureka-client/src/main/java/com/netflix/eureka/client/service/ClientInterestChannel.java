package com.netflix.eureka.client.service;

import com.netflix.eureka.interests.Interest;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.service.InterestChannel;
import rx.Observable;

/**
 * An extension to the {@link InterestChannel} for the client.
 *
 * <h2>Why do we need this?</h2>
 *
 * Eureka client uses a single outbound channel to a eureka read server and multiplexes different users requiring
 * different interest sets inside the application. In order to effectively manage the interest set for the entire
 * application without synchronizing, it is effective to leverage the single threaded nature of the
 * {@link InterestChannel} (forced via {@link InterestChannelInvoker}).
 * This extension provides way to modify the underlying interest set without requiring to atomically upgrade the
 * interest set using {@link #change(Interest)}
 *
 * @author Nitesh Kant
 */
interface ClientInterestChannel extends InterestChannel {

    /**
     * Appends the passed interest to the existing {@link Interest}.
     *
     * @param toAppend Interest to append.
     *
     * @return Acknowledgment as returned by {@link #change(Interest)} after appending this interest.
     */
    Observable<Void> appendInterest(Interest<InstanceInfo> toAppend);

    /**
     * Removes the passed interest to the existing {@link Interest}.
     *
     * @param toRemove Interest to remove.
     *
     * @return Acknowledgment as returned by {@link #change(Interest)} after removing this interest.
     */
    Observable<Void> removeInterest(Interest<InstanceInfo> toRemove);
}
