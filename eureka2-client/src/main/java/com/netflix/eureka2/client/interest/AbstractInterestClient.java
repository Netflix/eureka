package com.netflix.eureka2.client.interest;

import java.util.concurrent.atomic.AtomicBoolean;

import com.netflix.eureka2.channel.InterestChannel;
import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.connection.RetryableConnection;
import com.netflix.eureka2.registry.EurekaRegistry;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.utils.rx.RetryStrategyFunc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Scheduler;
import rx.Subscriber;

/**
 * Skeleton implementation of {@link EurekaInterestClient}, that provides common functions for code
 * reuse.
 *
 * @author Tomasz Bak
 */
public abstract class AbstractInterestClient implements EurekaInterestClient {

    private static final Logger logger = LoggerFactory.getLogger(AbstractInterestClient.class);

    public static final int DEFAULT_RETRY_WAIT_MILLIS = 1000;

    protected final EurekaRegistry<InstanceInfo> registry;
    protected final int retryWaitMillis;
    private final Scheduler scheduler;
    protected final AtomicBoolean isShutdown;

    protected AbstractInterestClient(final EurekaRegistry<InstanceInfo> registry,
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
            logger.info("Shutting down {}", this.getClass().getSimpleName());
            if (getRetryableConnection() != null) {
                getRetryableConnection().close();
            }
            registry.shutdown();
        }
    }

    protected abstract RetryableConnection<InterestChannel> getRetryableConnection();

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
