package com.netflix.eureka.client.service;

import com.netflix.eureka.client.transport.discovery.DiscoveryClient;
import com.netflix.eureka.client.transport.registration.RegistrationClient;
import com.netflix.eureka.interests.Interest;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.service.EurekaService;
import com.netflix.eureka.service.InterestChannel;
import com.netflix.eureka.service.RegistrationChannel;

/**
 * @author Nitesh Kant
 */
public class EurekaServiceImpl implements EurekaService {

    private final DiscoveryClient discoveryClient;
    private final RegistrationClient registrationClient;

    public EurekaServiceImpl(DiscoveryClient discoveryClient, RegistrationClient registrationClient) {
        this.discoveryClient = discoveryClient;
        this.registrationClient = registrationClient;
    }

    @Override
    public InterestChannel forInterest(Interest<InstanceInfo> interest) {
        return new InterestChannelImpl(discoveryClient, null); // TODO: Use the correct change notification in the client.
    }

    @Override
    public RegistrationChannel forRegistration(InstanceInfo instanceToRegister) {
        return new RegistrationChannelImpl(registrationClient, instanceToRegister);
    }
}
