package com.netflix.eureka2;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.netflix.eureka2.client.EurekaRegistrationClient;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.server.service.selfinfo.SelfInfoResolver;
import com.netflix.eureka2.server.service.SelfRegistrationService;
import rx.Observable;

/**
 * @author Tomasz Bak
 */
@Singleton
public class DashboardServerSelfRegistrationService extends SelfRegistrationService {

    private final EurekaRegistrationClient registrationClient;

    @Inject
    public DashboardServerSelfRegistrationService(SelfInfoResolver resolver, EurekaRegistrationClient eurekaClient) {
        super(resolver);
        this.registrationClient = eurekaClient;
    }

    @PostConstruct
    @Override
    public void init() {
        super.init();
    }

    @Override
    public Observable<Void> connect(Observable<InstanceInfo> registrant) {
        return registrationClient.register(registrant);
    }

    @Override
    public void cleanUpResources() {
        registrationClient.shutdown();
    }
}