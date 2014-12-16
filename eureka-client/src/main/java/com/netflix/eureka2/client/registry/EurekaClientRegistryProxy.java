package com.netflix.eureka2.client.registry;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.channel.RetryableEurekaChannelException;
import com.netflix.eureka2.client.channel.ClientChannelFactory;
import com.netflix.eureka2.client.channel.ClientInterestChannel;
import com.netflix.eureka2.client.channel.InterestChannelInvoker;
import com.netflix.eureka2.client.channel.RetryableInterestChannel;
import com.netflix.eureka2.client.metric.EurekaClientMetricFactory;
import com.netflix.eureka2.client.registry.swap.RegistrySwapStrategyFactory;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.registry.Delta;
import com.netflix.eureka2.registry.EurekaRegistry;
import com.netflix.eureka2.registry.InstanceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Scheduler;
import rx.Subscriber;
import rx.functions.Action0;
import rx.functions.Func1;
import rx.functions.Func2;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;

/**
 * An implementation of {@link EurekaRegistry} to be used by the eureka client.
 * It is a facade that is delegating registry requests to an internal registry
 * associated with an active channel. The channel failures are handled by
 * {@link com.netflix.eureka2.client.channel.RetryableInterestChannel}.
 *
 * @author Nitesh Kant
 */
@Singleton
public class EurekaClientRegistryProxy implements EurekaClientRegistry<InstanceInfo> {

    private static final Logger logger = LoggerFactory.getLogger(EurekaClientRegistryProxy.class);

    private final ClientInterestChannel interestChannel;

    @Inject
    public EurekaClientRegistryProxy(ClientChannelFactory channelFactory,
                                     RegistrySwapStrategyFactory swapStrategyFactory,
                                     long retryInitialDelayMs,
                                     EurekaClientMetricFactory metricFactory) {
        this(channelFactory, swapStrategyFactory, retryInitialDelayMs, metricFactory, Schedulers.computation());
    }

    public EurekaClientRegistryProxy(ClientChannelFactory channelFactory,
                                     RegistrySwapStrategyFactory swapStrategyFactory,
                                     long retryInitialDelayMs,
                                     EurekaClientMetricFactory metricFactory,
                                     Scheduler scheduler) {
        RetryableInterestChannel retryableInterestChannel =
                new RetryableInterestChannel(channelFactory, swapStrategyFactory, metricFactory, retryInitialDelayMs, scheduler);
        this.interestChannel = new InterestChannelInvoker(retryableInterestChannel, scheduler);
    }

    /* For testing. */EurekaClientRegistryProxy(RetryableInterestChannel retryableInterestChannel, Scheduler scheduler) {
        this.interestChannel = new InterestChannelInvoker(retryableInterestChannel, scheduler);
    }

    @Override
    public Observable<Void> register(InstanceInfo instanceInfo) {
        throw new IllegalArgumentException("Proxy registry modification not allowed");
    }

    @Override
    public Observable<Void> unregister(InstanceInfo instanceInfo) {
        throw new IllegalArgumentException("Proxy registry modification not allowed");
    }

    @Override
    public Observable<Void> update(InstanceInfo updatedInfo, Set<Delta<?>> deltas) {
        throw new IllegalArgumentException("Proxy registry modification not allowed");
    }

    @Override
    public int size() {
        return interestChannel.associatedRegistry().size();
    }

    @Override
    public Observable<InstanceInfo> forSnapshot(Interest<InstanceInfo> interest) {
        return interestChannel.associatedRegistry().forSnapshot(interest);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Observable<ChangeNotification<InstanceInfo>> forInterest(final Interest<InstanceInfo> interest) {
        Observable reply = interestChannel.appendInterest(interest)
                .cast(ChangeNotification.class)
                .mergeWith(interestChannel.associatedRegistry().forInterest(interest))
                .doOnTerminate(new Action0() {
                    @Override
                    public void call() {
                        interestChannel.removeInterest(interest).subscribe(new Subscriber<Void>() {
                            @Override
                            public void onCompleted() {
                            }

                            @Override
                            public void onError(Throwable e) {
                                logger.warn("forInterest unsubscribe failed", e);
                            }

                            @Override
                            public void onNext(Void aVoid) {
                            }
                        });
                    }
                });
        return reply;
    }

    @Override
    public Observable<Void> shutdown() {
        interestChannel.close();
        return Observable.empty();
    }

    @Override
    public Observable<Void> shutdown(Throwable cause) {
        throw new IllegalArgumentException("EurekaClientRegistryProxy supports regular shutdown only");
    }
}
