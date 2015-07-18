package com.netflix.eureka2.client.interest;

import java.util.concurrent.atomic.AtomicBoolean;

import com.netflix.eureka2.channel.InterestChannel;
import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.connection.RetryableConnection;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.StreamStateNotification;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.utils.rx.NoOpSubscriber;
import com.netflix.eureka2.utils.rx.RetryStrategyFunc;
import com.netflix.eureka2.utils.rx.RxFunctions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Notification;
import rx.Observable;
import rx.Scheduler;
import rx.Subscriber;
import rx.functions.Func1;
import rx.functions.Func2;

/**
 * Skeleton implementation of {@link EurekaInterestClient}, that provides common functions for code
 * reuse.
 *
 * @author Tomasz Bak
 */
public abstract class AbstractInterestClient implements EurekaInterestClient {

    private static final Logger logger = LoggerFactory.getLogger(AbstractInterestClient.class);

    public static final int DEFAULT_RETRY_WAIT_MILLIS = 1000;

    protected final SourcedEurekaRegistry<InstanceInfo> registry;
    protected final int retryWaitMillis;
    private final Scheduler scheduler;
    protected final AtomicBoolean isShutdown;

    protected AbstractInterestClient(final SourcedEurekaRegistry<InstanceInfo> registry,
                                     int retryWaitMillis,
                                     Scheduler scheduler) {
        this.registry = registry;
        this.retryWaitMillis = retryWaitMillis;
        this.scheduler = scheduler;
        this.isShutdown = new AtomicBoolean(false);
    }

    @Override
    public void shutdown() {
        if (isShutdown.compareAndSet(false, true)) {
            logger.info("Shutting down InterestClient");
            if (getRetryableConnection() != null) {
                getRetryableConnection().close();
            }
            registry.shutdown();
        }
    }

    protected abstract RetryableConnection<InterestChannel> getRetryableConnection();

    private Observable<Long> setUpEviction(final InterestChannel prev, final InterestChannel curr) {
        // once a new channel is available, wait for the first bufferEnd to be emitted. Once it is emitted,
        // return a reference to the previous channel.
        return curr.getChangeNotificationStream()
                .filter(new Func1<ChangeNotification<InstanceInfo>, Boolean>() {
                    @Override
                    public Boolean call(ChangeNotification<InstanceInfo> notification) {
                        if (notification instanceof StreamStateNotification) {
                            StreamStateNotification<InstanceInfo> n = (StreamStateNotification<InstanceInfo>) notification;
                            if (n.getBufferState() == StreamStateNotification.BufferState.BufferEnd) {
                                return true;
                            }
                        }
                        return false;
                    }
                })
                .take(1)
                .map(new Func1<ChangeNotification<InstanceInfo>, Source>() {
                    @Override
                    public Source call(ChangeNotification<InstanceInfo> notification) {
                        return curr.getSource();
                    }
                })
                .filter(RxFunctions.filterNullValuesFunc())  // for paranoia
                .flatMap(new Func1<Source, Observable<Long>>() {
                    @Override
                    public Observable<Long> call(final Source currSource) {
                        return prev.asLifecycleObservable()
                                .materialize()
                                .flatMap(new Func1<Notification<Void>, Observable<Long>>() {
                                    @Override
                                    public Observable<Long> call(Notification<Void> rxNotification) {
                                        // wait for the old channel to be closed before starting the eviction
                                        // since the input is a void observable OnError or OnCompleted are both fine
                                        Source.SourceMatcher evictAllOlderMatcher = new Source.SourceMatcher() {
                                            @Override
                                            public boolean match(Source another) {
                                                if (another.getOrigin() == currSource.getOrigin() &&
                                                        another.getName().equals(currSource.getName()) &&
                                                        another.getId() < currSource.getId()) {
                                                    return true;
                                                }
                                                return false;
                                            }
                                            @Override
                                            public String toString() {
                                                return "evictAllOlderMatcher{" + currSource + "}";
                                            }
                                        };

                                        return registry.evictAll(evictAllOlderMatcher);
                                    }
                                });
                    }
                });
    }

    // FIXME this is hacky (will go away once we remove the SourcedRegistry from the client)
    protected void registryEvictionSubscribe(RetryableConnection<InterestChannel> retryableConnection) {
        // subscribe to the base interest channels to do cleanup on every channel refresh.
        retryableConnection.getChannelObservable()
                .scan(Observable.<InterestChannel>empty(), new Func2<Observable<InterestChannel>, InterestChannel, Observable<InterestChannel>>() {
                    @Override
                    public Observable<InterestChannel> call(final Observable<InterestChannel> initial, final InterestChannel curr) {
                        // once a new channel is available, wait for the first bufferEnd to be emitted. Once it is emitted,
                        // return a reference to the previous channel.
                        initial.take(1)
                                .flatMap(new Func1<InterestChannel, Observable<Long>>() {
                                    @Override
                                    public Observable<Long> call(InterestChannel prev) {
                                        return setUpEviction(prev, curr);
                                    }
                                })
                                .subscribe(new Subscriber<Long>() {
                                    @Override
                                    public void onCompleted() {
                                    }

                                    @Override
                                    public void onError(Throwable e) {
                                    }

                                    @Override
                                    public void onNext(Long aLong) {
                                        logger.info("Evicted {} instances in one round of eviction due to a new interestChannel creation", aLong);
                                    }

                                });

                        return Observable.just(curr);
                    }
                })
                .subscribe(new NoOpSubscriber<>());
    }

    protected void lifecycleSubscribe(RetryableConnection<InterestChannel> retryableConnection) {
        // subscribe to the lifecycle to initiate the interest subscription
        retryableConnection.getRetryableLifecycle()
                .retryWhen(new RetryStrategyFunc(retryWaitMillis, scheduler))
                .subscribe(new Subscriber<Void>() {
                    @Override
                    public void onCompleted() {
                        logger.info("channel onCompleted");
                    }

                    @Override
                    public void onError(Throwable e) {
                        logger.error("Lifecycle closed with an error");
                    }

                    @Override
                    public void onNext(Void aVoid) {

                    }
                });
    }
}
