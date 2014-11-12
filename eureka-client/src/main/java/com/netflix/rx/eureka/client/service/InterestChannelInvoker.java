package com.netflix.rx.eureka.client.service;

import com.netflix.rx.eureka.interests.Interest;
import com.netflix.rx.eureka.registry.InstanceInfo;
import com.netflix.rx.eureka.service.InterestChannel;
import com.netflix.rx.eureka.utils.SerializedTaskInvoker;
import rx.Observable;

import java.util.concurrent.Callable;

/**
 * A decorator of {@link InterestChannel} which delegates to an actual {@link InterestChannel} making sure that all
 * operations on the underlying channel are strictly sequenced in the order they arrive on this channel.
 *
 * @author Nitesh Kant
 */
/*pkg-private: Used by EurekaClientService only*/class InterestChannelInvoker extends SerializedTaskInvoker
        implements ClientInterestChannel {

    private final ClientInterestChannel delegate;

    InterestChannelInvoker(ClientInterestChannel delegate) {
        this.delegate = delegate;
    }

    @Override
    public Observable<Void> change(final Interest<InstanceInfo> newInterest) {
        return submitForAck(new Callable<Observable<Void>>() {
            @Override
            public Observable<Void> call() throws Exception {
                return delegate.change(newInterest);
            }
        });
    }

    @Override
    public void close() {
        try {
            shutdown();
        } finally {
            delegate.close();
        }
    }

    @Override
    public Observable<Void> asLifecycleObservable() {
        return delegate.asLifecycleObservable();
    }

    @Override
    public Observable<Void> appendInterest(final Interest<InstanceInfo> toAppend) {
        return submitForAck(new Callable<Observable<Void>>() {
            @Override
            public Observable<Void> call() throws Exception {
                return delegate.appendInterest(toAppend);
            }
        });
    }

    @Override
    public Observable<Void> removeInterest(final Interest<InstanceInfo> toRemove) {
        return submitForAck(new Callable<Observable<Void>>() {
            @Override
            public Observable<Void> call() throws Exception {
                return delegate.removeInterest(toRemove);
            }
        });
    }
}
