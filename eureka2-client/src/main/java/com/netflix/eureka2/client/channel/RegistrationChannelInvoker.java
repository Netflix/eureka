package com.netflix.eureka2.client.channel;

import java.util.concurrent.Callable;

import com.netflix.eureka2.channel.RegistrationChannel;
import com.netflix.eureka2.metric.SerializedTaskInvokerMetrics;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.utils.SerializedTaskInvoker;
import rx.Observable;

/**
 * A decorator of {@link RegistrationChannel} which delegates to an actual {@link RegistrationChannel} making sure that
 * all operations on the underlying channel are strictly sequenced in the order they arrive on this channel.
 *
 * @author Nitesh Kant
 */
/*pkg-private: Used by EurekaClientService only*/class RegistrationChannelInvoker
        extends SerializedTaskInvoker implements RegistrationChannel {

    private final RegistrationChannel delegate;

    public RegistrationChannelInvoker(RegistrationChannel delegate, SerializedTaskInvokerMetrics metrics) {
        super(metrics);
        this.delegate = delegate;
    }

    @Override
    public Observable<Void> register(final InstanceInfo instanceInfo) {
        return submitForAck(new Callable<Observable<Void>>() {
            @Override
            public Observable<Void> call() throws Exception {
                return delegate.register(instanceInfo);
            }

            @Override
            public String toString() {
                return "RegistrationChannelInvoker - Register: " + instanceInfo;
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

            @Override
            public String toString() {
                return "RegistrationChannelInvoker - Unregister";
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
