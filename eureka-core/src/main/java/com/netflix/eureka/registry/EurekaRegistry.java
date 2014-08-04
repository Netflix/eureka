package com.netflix.eureka.registry;

import com.netflix.eureka.interests.ChangeNotification;
import com.netflix.eureka.interests.Interest;
import rx.Observable;

/**
 * @author Nitesh Kant
 */
public interface EurekaRegistry {

    Observable<Void> register(InstanceInfo instanceInfo);

    Observable<Void> unregister(String instanceId);

    Observable<Void> update(InstanceInfo instanceInfo);

    Observable<ChangeNotification<InstanceInfo>> forInterest(Interest<InstanceInfo> interest);

}
