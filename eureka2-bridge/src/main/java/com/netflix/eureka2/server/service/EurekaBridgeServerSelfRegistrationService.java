package com.netflix.eureka2.server.service;

import com.netflix.eureka2.server.registry.EurekaRegistrationProcessor;
import com.netflix.eureka2.server.service.selfinfo.SelfInfoResolver;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @author David Liu
 */
@Singleton
public class EurekaBridgeServerSelfRegistrationService extends EurekaWriteServerSelfRegistrationService {

    @Inject
    public EurekaBridgeServerSelfRegistrationService(SelfInfoResolver resolver, EurekaRegistrationProcessor registrationProcessor) {
        super(resolver, registrationProcessor);
    }
}
