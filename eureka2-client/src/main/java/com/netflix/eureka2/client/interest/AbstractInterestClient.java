package com.netflix.eureka2.client.interest;

import java.util.concurrent.atomic.AtomicBoolean;

import com.netflix.eureka2.channel.InterestChannel;
import com.netflix.eureka2.connection.RetryableConnection;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.Sourced;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.utils.rx.RetryStrategyFunc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Func1;

/**
 * Skeleton implementation of {@link EurekaInterestClient}, that provides common functions for code
 * reuse.
 *
 * @author Tomasz Bak
 */
public abstract class AbstractInterestClient implements EurekaInterestClient {

    private static final Logger logger = LoggerFactory.getLogger(AbstractInterestClient.class);

    public static final int DEFAULT_RETRY_WAIT_MILLIS = 500;

    protected final SourcedEurekaRegistry<InstanceInfo> registry;
    protected final int retryWaitMillis;
    protected final AtomicBoolean isShutdown;

    protected AbstractInterestClient(final SourcedEurekaRegistry<InstanceInfo> registry,
                                     int retryWaitMillis) {
        this.registry = registry;
        this.retryWaitMillis = retryWaitMillis;
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

    protected void registryEvictionSubscribe(RetryableConnection<InterestChannel> retryableConnection) {
        // subscribe to the base interest channels to do cleanup on every channel refresh.
        retryableConnection.getChannelObservable()
                .flatMap(new Func1<InterestChannel, Observable<Long>>() {
                    @Override
                    public Observable<Long> call(InterestChannel interestChannel) {
                        if (interestChannel instanceof Sourced) {
                            Source toRetain = ((Sourced) interestChannel).getSource();
                            return registry.evictAllExcept(Source.matcherFor(toRetain));
                        }
                        return Observable.empty();
                    }
                })
                .subscribe(new Subscriber<Long>() {
                    @Override
                    public void onCompleted() {
                        logger.info("Completed one round of eviction due to a new interestChannel creation");
                    }

                    @Override
                    public void onError(Throwable e) {
                        logger.warn("OnError in one round of eviction due to a new interestChannel creation");
                    }

                    @Override
                    public void onNext(Long aLong) {
                        logger.info("Evicted {} instances in one round of eviction due to a new interestChannel creation", aLong);
                    }
                });
    }

    protected void lifecycleSubscribe(RetryableConnection<InterestChannel> retryableConnection) {
        // subscribe to the lifecycle to initiate the interest subscription
        retryableConnection.getRetryableLifecycle()
                .retryWhen(new RetryStrategyFunc(retryWaitMillis))
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
