package com.netflix.eureka2.channel;

import rx.Observable;

/**
 * A {@link ServiceChannel} is a medium to define eureka protocols for modification to the
 * {@link com.netflix.eureka2.registry.SourcedEurekaRegistry}.
 *
 * As is the case with a typical channel, there are two ends to a channel, viz.
 *
 * <h2>Sender</h2>
 * A party that writes to this channel.
 *
 * <h2>Receiver</h2>
 * A party that reads from this channel.
 *
 * @author Nitesh Kant
 */
public interface ServiceChannel {

    /**
     * Closes this channel.
     */
    void close();

    /**
     * Returns an {@link Observable} for the lifecycle of this channel.
     *
     * @return An {@link Observable} for the lifecycle of this channel which completes when the channel closes.
     */
    Observable<Void> asLifecycleObservable();
}
