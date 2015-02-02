package com.netflix.eureka2.server.service;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.Sourced;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import rx.Observable;

/**
 * @author David Liu
 */
@Singleton
public class EurekaWriteServerSelfRegistrationService extends SelfRegistrationService implements Sourced {

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
    public void cleanUpResources() {
        // no-op
    }

    @Override
    public Observable<Void> register(final InstanceInfo instanceInfo) {
        return registry.register(instanceInfo, selfSource).ignoreElements().cast(Void.class);
    }

    @Override
    public Observable<Void> unregister(InstanceInfo instanceInfo) {
        return registry.unregister(instanceInfo, selfSource).ignoreElements().cast(Void.class);
    }

    @Override
    public Source getSource() {
        return selfSource;
    }
}
