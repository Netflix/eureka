package com.netflix.eureka2.server.service;

import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.server.service.selfinfo.SelfInfoResolver;
import com.netflix.eureka2.utils.rx.LoggingSubscriber;
import com.netflix.eureka2.utils.rx.RetryStrategyFunc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscriber;

import javax.annotation.PreDestroy;

/**
 * A self identify service that periodically reports data
 * @author David Liu
 */
public abstract class SelfRegistrationService implements SelfInfoResolver {

    private static final Logger logger = LoggerFactory.getLogger(SelfRegistrationService.class);

    private final SelfInfoResolver resolver;
    private final Subscriber<Void> localSubscriber;

    public SelfRegistrationService(SelfInfoResolver resolver) {
        this.resolver = resolver;
        this.localSubscriber = new LoggingSubscriber<>(logger);
    }

    public void init() {
        Observable<InstanceInfo> selfInfoStream = resolve().distinctUntilChanged();
        connect(selfInfoStream).retryWhen(new RetryStrategyFunc(500, TimeUnit.MILLISECONDS)).subscribe(localSubscriber);
    }

    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down the self registration service");

        cleanUpResources();

        if (!localSubscriber.isUnsubscribed()) {
            localSubscriber.unsubscribe();
        }
    }

    @Override
    public Observable<InstanceInfo> resolve() {
        return resolver.resolve();
    }

    public abstract void cleanUpResources();

    public abstract Observable<Void> connect(Observable<InstanceInfo> registrant);
}
