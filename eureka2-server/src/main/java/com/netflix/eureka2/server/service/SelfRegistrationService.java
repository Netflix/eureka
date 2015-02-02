package com.netflix.eureka2.server.service;

import java.util.concurrent.atomic.AtomicReference;

import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.utils.rx.RetryStrategyFunc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscription;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;

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
        subscription = resolve()
                .distinctUntilChanged()
                .switchMap(new Func1<InstanceInfo, Observable<? extends Void>>() {
                    @Override
                    public Observable<Void> call(final InstanceInfo instanceInfo) {
                        latestSelfInfo.set(instanceInfo);

                        return register(instanceInfo)
                                .doOnError(new Action1<Throwable>() {
                                    @Override
                                    public void call(Throwable throwable) {
                                        logger.warn("Error reporting updated self info {}", instanceInfo, throwable);
                                    }
                                })
                                .doOnCompleted(new Action0() {
                                    @Override
                                    public void call() {
                                        logger.info("Reported updated self info {}", instanceInfo);
                                    }
                                });
                    }
                })
                .retryWhen(new RetryStrategyFunc(500, 3, true))
                .subscribe();
    }

    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down the self registration service");
        if (subscription != null) {
            subscription.unsubscribe();
        }
        if (latestSelfInfo.get() != null) {
            unregister(latestSelfInfo.get())
                    .doOnCompleted(new Action0() {
                        @Override
                        public void call() {
                            logger.info("Unregistered own instance info: {}", latestSelfInfo.get());
                        }
                    })
                    .doOnError(new Action1<Throwable>() {
                        @Override
                        public void call(Throwable throwable) {
                            logger.warn("Error during self unregistration process: {}", latestSelfInfo);
                        }
                    }).materialize().toBlocking().firstOrDefault(null);  // FIXME why materialize and block here?
        }

        cleanUpResources();
    }

    @Override
    public Observable<InstanceInfo> resolve() {
        return resolver.resolve();
    }

    public abstract void cleanUpResources();

    public abstract Observable<Void> register(InstanceInfo instanceInfo);

    public abstract Observable<Void> unregister(InstanceInfo instanceInfo);
}
