package com.netflix.rx.eureka.client.service;

import com.netflix.rx.eureka.registry.InstanceInfo;
import com.netflix.rx.eureka.service.RegistrationChannel;
import com.netflix.rx.eureka.utils.SerializedTaskInvoker;
import rx.Observable;

import java.util.concurrent.Callable;

/**
 * A decorator of {@link RegistrationChannel} which delegates to an actual {@link RegistrationChannel} making sure that
 * all operations on the underlying channel are strictly sequenced in the order they arrive on this channel.
 *
 * @author Nitesh Kant
 */
/*pkg-private: Used by EurekaClientService only*/class RegistrationChannelInvoker
        extends SerializedTaskInvoker implements RegistrationChannel {

    private final RegistrationChannel delegate;

    public RegistrationChannelInvoker(RegistrationChannel delegate) {
        this.delegate = delegate;
    }

    @Override
    public Observable<Void> register(final InstanceInfo instanceInfo) {
        return submitForAck(new Callable<Observable<Void>>() {
            @Override
            public Observable<Void> call() throws Exception {
                return delegate.register(instanceInfo);
            }
        });
    }

    @Override
    public Observable<Void> update(final InstanceInfo newInfo) {
        return submitForAck(new Callable<Observable<Void>>() {
            @Override
            public Observable<Void> call() throws Exception {
                return delegate.update(newInfo);
            }
        });
    }

    @Override
    public Observable<Void> unregister() {
        return submitForAck(new Callable<Observable<Void>>() {
            @Override
            public Observable<Void> call() throws Exception {
                return delegate.unregister();
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
}
