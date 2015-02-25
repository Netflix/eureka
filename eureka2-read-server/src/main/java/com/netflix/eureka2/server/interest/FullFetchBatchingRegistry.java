package com.netflix.eureka2.server.interest;

import com.netflix.eureka2.client.interest.BatchingRegistry;
import com.netflix.eureka2.client.interest.BatchingRegistryImpl;
import com.netflix.eureka2.interests.FullRegistryInterest;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.StreamStateNotification.BufferState;
import rx.Observable;

/**
 * A special implementation of {@link BatchingRegistry}, where client interest channel contains
 * single full registry fetch subscription, and thus fine grain, atomic interest streams are not
 * available.
 * <p>
 * Batch markers are generated from full registry fetch stream, and are shared by all
 * subscribers, irrespective of their interest subscriptions. The only adverse effect on the end
 * client is possible extra delay incurred, before buffer sentinel is generated.
 *
 * @author Tomasz Bak
 */
public class FullFetchBatchingRegistry<T> extends BatchingRegistryImpl<T> {

    private final FullRegistryInterest<T> fullRegistryInterest = FullRegistryInterest.getInstance();

    @Override
    public Observable<BufferState> forInterest(Interest<T> interest) {
        return super.forInterest(fullRegistryInterest);
    }

    @Override
    public BufferState shouldBatch(Interest<T> interest) {
        return super.shouldBatch(fullRegistryInterest);
    }
}
