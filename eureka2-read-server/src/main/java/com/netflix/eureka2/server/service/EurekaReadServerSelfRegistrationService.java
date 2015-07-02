package com.netflix.eureka2.server.service;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.netflix.eureka2.client.EurekaRegistrationClient;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.server.service.selfinfo.SelfInfoResolver;
import rx.Observable;

/**
 * @author David Liu
 */
@Singleton
public class EurekaReadServerSelfRegistrationService extends SelfRegistrationService {

    private final EurekaRegistrationClient registrationClient;

    @Inject
    public EurekaReadServerSelfRegistrationService(SelfInfoResolver resolver, EurekaRegistrationClient registrationClient) {
        super(resolver);
        this.registrationClient = registrationClient;
    }

    @PostConstruct
    @Override
    public void init() {
        super.init();
    }

    @Override
    public void cleanUpResources() {
        registrationClient.shutdown();
    }

    @Override
    public Observable<Void> connect(Observable<InstanceInfo> registrant) {
        return registrationClient.register(registrant);
    }
}
