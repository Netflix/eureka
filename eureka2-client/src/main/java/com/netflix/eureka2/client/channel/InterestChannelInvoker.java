package com.netflix.eureka2.client.channel;

import java.util.concurrent.Callable;

import com.netflix.eureka2.channel.InterestChannel;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.metric.client.EurekaClientMetricFactory;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.utils.SerializedTaskInvoker;
import rx.Observable;
import rx.Scheduler;
import rx.schedulers.Schedulers;

/**
 * A decorator of {@link InterestChannel} which delegates to an actual {@link InterestChannel} making sure that all
 * operations on the underlying channel are strictly sequenced in the order they arrive on this channel.
 *
 * @author Nitesh Kant
 */
public class InterestChannelInvoker extends SerializedTaskInvoker implements InterestChannel {

    private final InterestChannel delegate;

    public InterestChannelInvoker(InterestChannel delegate, EurekaClientMetricFactory metricFactory) {
        this(delegate, metricFactory, Schedulers.computation());
    }

    public InterestChannelInvoker(InterestChannel delegate, EurekaClientMetricFactory metricFactory, Scheduler scheduler) {
        super(metricFactory.getSerializedTaskInvokerMetrics(InterestChannelInvoker.class), scheduler);
        this.delegate = delegate;
    }

    @Override
    public Observable<Void> change(final Interest<InstanceInfo> newInterest) {
        return submitForAck(new Callable<Observable<Void>>() {
            @Override
            public Observable<Void> call() throws Exception {
                return delegate.change(newInterest);
            }

            @Override
            public String toString() {
                return "change: " + newInterest;
            }
        });
    }

    @Override
    public Observable<ChangeNotification<InstanceInfo>> getChangeNotificationStream() {
        return delegate.getChangeNotificationStream();
    }

    @Override
    public void close() {
        try {
            shutdownTaskInvoker();
        } finally {
            delegate.close();
        }
    }

    @Override
    public void close(Throwable error) {
        try {
            shutdownTaskInvoker();
        } finally {
            delegate.close(error);
        }
    }

    @Override
    public Observable<Void> asLifecycleObservable() {
        return delegate.asLifecycleObservable();
    }

    @Override
    public Source getSource() {
        return delegate.getSource();
    }
}
