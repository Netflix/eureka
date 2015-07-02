package com.netflix.eureka2.server.service;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.server.service.selfinfo.SelfInfoResolver;
import com.netflix.eureka2.utils.rx.RetryStrategyFunc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscription;

import javax.annotation.PreDestroy;

/**
 * A self identify service that periodically reports data
 * @author David Liu
 */
public abstract class SelfRegistrationService implements SelfInfoResolver {

    private static final Logger logger = LoggerFactory.getLogger(SelfRegistrationService.class);

    private final SelfInfoResolver resolver;

    private final AtomicReference<InstanceInfo> latestSelfInfo = new AtomicReference<>();
    private Subscription subscription;

    public SelfRegistrationService(SelfInfoResolver resolver) {
        this.resolver = resolver;
    }

    public void init() {
        Observable<InstanceInfo> selfInfoStream = resolve().distinctUntilChanged();
        subscription = connect(selfInfoStream).retryWhen(new RetryStrategyFunc(500, TimeUnit.MILLISECONDS)).subscribe();
    }

    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down the self registration service");

        if (subscription != null) {
            subscription.unsubscribe();
        }

        cleanUpResources();
    }

    @Override
    public Observable<InstanceInfo> resolve() {
        return resolver.resolve();
    }

    public abstract void cleanUpResources();

    public abstract Observable<Void> connect(Observable<InstanceInfo> registrant);
}
