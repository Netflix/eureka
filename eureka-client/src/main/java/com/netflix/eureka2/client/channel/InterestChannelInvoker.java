package com.netflix.eureka2.client.channel;

import java.util.concurrent.Callable;

import com.netflix.eureka2.channel.InterestChannel;
import com.netflix.eureka2.client.registry.EurekaClientRegistry;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.metric.SerializedTaskInvokerMetrics;
import com.netflix.eureka2.registry.InstanceInfo;
import com.netflix.eureka2.utils.SerializedTaskInvoker;
import rx.Observable;
import rx.Scheduler;

/**
 * A decorator of {@link InterestChannel} which delegates to an actual {@link InterestChannel} making sure that all
 * operations on the underlying channel are strictly sequenced in the order they arrive on this channel.
 *
 * @author Nitesh Kant
 */
public class InterestChannelInvoker extends SerializedTaskInvoker
        implements ClientInterestChannel {

    private final ClientInterestChannel delegate;

    public InterestChannelInvoker(ClientInterestChannel delegate, Scheduler scheduler) {
        // TODO: add invoker metrics to the client
        super(SerializedTaskInvokerMetrics.dummyMetrics(), scheduler);
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
    public EurekaClientRegistry<InstanceInfo> associatedRegistry() {
        return delegate.associatedRegistry();
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
