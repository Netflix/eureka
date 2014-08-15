package com.netflix.eureka.server.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.netflix.eureka.interests.ChangeNotification;
import com.netflix.eureka.interests.Interest;
import com.netflix.eureka.registry.EurekaRegistry;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.service.InterestChannel;
import com.netflix.eureka.service.RegistrationChannel;
import rx.Observable;

/**
 * @author Nitesh Kant
 */
@Singleton
public class EurekaServiceImpl implements EurekaServerService {

    private final EurekaRegistry registry;

    @Inject
    public EurekaServiceImpl(EurekaRegistry registry) {
        this.registry = registry;
    }

    @Override
    public InterestChannel forInterest(Interest<InstanceInfo> interest) {
        Observable<ChangeNotification<InstanceInfo>> stream = registry.forInterest(interest);
        return new InterestChannelImpl(stream);
    }

    @Override
    public RegistrationChannel newRegistrationChannel() {
        return new RegistrationChannelImpl(registry);
    }

    @Override
    public ReplicationChannel newReplicationChannel(InstanceInfo sourceServer) {
        return new ReplicationChannelImpl(sourceServer);
    }
}
