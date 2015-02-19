package com.netflix.eureka2.server.service;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.functions.Action0;
import rx.functions.Func1;

import java.util.concurrent.atomic.AtomicReference;

/**
 * @author David Liu
 */
@Singleton
public class EurekaWriteServerSelfRegistrationService extends SelfRegistrationService {

    private static final Logger logger = LoggerFactory.getLogger(EurekaWriteServerSelfRegistrationService.class);

    private final SourcedEurekaRegistry<InstanceInfo> registry;
    private final Source selfSource;

    @Inject
    public EurekaWriteServerSelfRegistrationService(SelfInfoResolver resolver, SourcedEurekaRegistry registry) {
        super(resolver);
        this.registry = registry;
        this.selfSource = new Source(Source.Origin.LOCAL);
    }

    @PostConstruct
    @Override
    public void init() {
        super.init();
    }

    @Override
    public Observable<Void> connect(final Observable<InstanceInfo> registrant) {
        final AtomicReference<InstanceInfo> instanceInfoRef = new AtomicReference<>();
        return registrant.replay(1).refCount()
                .flatMap(new Func1<InstanceInfo, Observable<Void>>() {
                    @Override
                    public Observable<Void> call(InstanceInfo instanceInfo) {
                        logger.info("registering self InstanceInfo {}", instanceInfo);
                        instanceInfoRef.set(instanceInfo);
                        return registry.register(instanceInfo, selfSource).ignoreElements().cast(Void.class);
                    }
                })
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        InstanceInfo info = instanceInfoRef.getAndSet(null);
                        if (info != null) {
                            logger.info("unregistering self InstanceInfo {}", info);
                            registry.unregister(info, selfSource).subscribe();
                        }
                    }
                });
    }

    @Override
    public void cleanUpResources() {
        // no-op
    }
}
