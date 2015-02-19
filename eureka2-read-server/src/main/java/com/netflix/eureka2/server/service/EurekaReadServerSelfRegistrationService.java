package com.netflix.eureka2.server.service;

import com.netflix.eureka2.client.EurekaClient;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import rx.Observable;

import javax.annotation.PostConstruct;
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

    @Override
    public void cleanUpResources() {
        eurekaClient.shutdown();
    }

    @Override
    public Observable<Void> connect(Observable<InstanceInfo> registrant) {
        return eurekaClient.register(registrant);
    }
}
