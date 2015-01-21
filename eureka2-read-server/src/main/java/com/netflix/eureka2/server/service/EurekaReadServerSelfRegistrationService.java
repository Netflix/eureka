package com.netflix.eureka2.server.service;

import com.netflix.eureka2.client.EurekaClient;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import rx.Observable;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @author David Liu
 */
@Singleton
public class EurekaReadServerSelfRegistrationService extends SelfRegistrationService {

    private final EurekaClient eurekaClient;

    @Inject
    public EurekaReadServerSelfRegistrationService(SelfInfoResolver resolver, EurekaClient eurekaClient) {
        super(resolver);
        this.eurekaClient = eurekaClient;
    }

    @PostConstruct
    @Override
    public void init() {
        super.init();
    }

    @PreDestroy
    public void shutdown() {
        eurekaClient.close();
    }

    @Override
    public Observable<Void> register(final InstanceInfo instanceInfo) {
        return eurekaClient.register(instanceInfo);
    }

    @Override
    public Observable<Void> unregister(InstanceInfo instanceInfo) {
        return eurekaClient.unregister(instanceInfo);
    }
}
