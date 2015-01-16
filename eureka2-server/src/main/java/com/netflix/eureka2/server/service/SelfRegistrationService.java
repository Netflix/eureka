package com.netflix.eureka2.server.service;

import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.utils.rx.RetryStrategyFunc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;

/**
 * A self identify service that periodically reports data
 * @author David Liu
 */
public abstract class SelfRegistrationService implements SelfInfoResolver {

    private static final Logger logger = LoggerFactory.getLogger(SelfRegistrationService.class);

    private final SelfInfoResolver resolver;

    public SelfRegistrationService(SelfInfoResolver resolver) {
        this.resolver = resolver;
    }

    public void init() {
        resolve()
                .distinctUntilChanged()
                .switchMap(new Func1<InstanceInfo, Observable<? extends Void>>() {
                    @Override
                    public Observable<Void> call(final InstanceInfo instanceInfo) {
                        return report(instanceInfo)
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

    @Override
    public Observable<InstanceInfo> resolve() {
        return resolver.resolve();
    }

    public abstract Observable<Void> report(InstanceInfo instanceInfo);
}
