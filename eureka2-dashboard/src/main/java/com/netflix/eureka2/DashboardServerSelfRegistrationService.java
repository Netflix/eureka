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
    public void cleanUpResources() {
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