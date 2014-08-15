package com.netflix.eureka.client.service;

import com.netflix.eureka.client.transport.TransportClientProvider;
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

    private final RegistrationChannelMonitor registrationChannelMonitor;
    private final DiscoveryChannelMonitor discoveryChannelMonitor;

    public EurekaServiceImpl(TransportClientProvider<DiscoveryClient> discoveryClientProvider,
                             TransportClientProvider<RegistrationClient> registrationClientProvider) {
        registrationChannelMonitor = new RegistrationChannelMonitor(registrationClientProvider);
        registrationChannelMonitor.start();

        discoveryChannelMonitor = new DiscoveryChannelMonitor(discoveryClientProvider);
        discoveryChannelMonitor.start();
    }

    @Override
    public InterestChannel forInterest(Interest<InstanceInfo> interest) {
        InterestChannel interestChannel = discoveryChannelMonitor.activeChannel();
        interestChannel.upgrade(interest);
        return interestChannel;
    }

    @Override
    public RegistrationChannel newRegistrationChannel() {
        return registrationChannelMonitor.activeChannel();
    }
}
