package com.netflix.eureka.client.service;

import com.netflix.eureka.interests.ChangeNotification;
import com.netflix.eureka.interests.Interest;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.service.EurekaService;
import rx.Observable;

/**
 * Each EurekaClientService should correspond to a single eureka Client and provide methods to access the underlying
 * registry information.
 * TODO: seems like we can merge this with EurekaClient
 *
 * @author David Liu
 */
public interface EurekaClientService extends EurekaService {
    /**
     * Provide a way to access the registry of the underlying eurekaRegistry
     * @param interest
     * @return
     */
    Observable<ChangeNotification<InstanceInfo>> forInterest(Interest<InstanceInfo> interest);
}
