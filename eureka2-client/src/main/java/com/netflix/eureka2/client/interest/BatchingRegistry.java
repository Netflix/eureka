package com.netflix.eureka2.client.interest;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.StreamStateNotification.BufferState;
import rx.Observable;

/**
 * This class collects information about batching statuses of individual atomic interests.
 * It is assumed that state manipulation methods are serialized.
 *
 * @author Tomasz Bak
 */
public interface BatchingRegistry<T> {

    /**
     * Change notifications stream to observe. Subsequent subscribe invocation unsubscribe the
     * previous subscription, and connect a new one.
     */
    void connectTo(Observable<ChangeNotification<T>> changeNotifications);

    /**
     * Issue batching state updates ({@link com.netflix.eureka2.interests.StreamStateNotification.BufferState#BufferStart}
     * or {@link com.netflix.eureka2.interests.StreamStateNotification.BufferState#BufferEnd}).
     */
    Observable<BufferState> forInterest(Interest<T> interest);

    /**
     * Return batching state for a given atomic or composite interest.
     */
    BufferState shouldBatch(Interest<T> interest);

    /**
     * Retain internal state information for the specified composite interest.
     */
    void retainAll(Interest<T> interest);

    /**
     * Disconnect subscribers and cleanup the internal state.
     */
    void shutdown();
}
