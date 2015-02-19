package com.netflix.eureka2;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.netflix.eureka2.client.EurekaClient;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.server.service.SelfInfoResolver;
import com.netflix.eureka2.server.service.SelfRegistrationService;
import rx.Observable;

/**
 * @author Tomasz Bak
 */
@Singleton
public class DashboardServerSelfRegistrationService extends SelfRegistrationService {

    private final EurekaClient eurekaClient;

    @Inject
    public DashboardServerSelfRegistrationService(SelfInfoResolver resolver, EurekaClient eurekaClient) {
        super(resolver);
        this.eurekaClient = eurekaClient;
    }

    @PostConstruct
    @Override
    public void init() {
        super.init();
    }

    @Override
    public Observable<Void> connect(Observable<InstanceInfo> registrant) {
        return eurekaClient.register(registrant);
    }

    @Override
    public void cleanUpResources() {
        eurekaClient.shutdown();
    }
}