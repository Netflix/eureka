package com.netflix.eureka2.client.channel;

import com.netflix.eureka2.client.registry.EurekaClientRegistry;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.registry.InstanceInfo;
import com.netflix.eureka2.channel.InterestChannel;
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
public interface ClientInterestChannel extends InterestChannel {

    /**
     * Return a registry instance associated with this channel.
     */
    EurekaClientRegistry<InstanceInfo> associatedRegistry();

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
