package com.netflix.eureka2.client.interest;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import rx.Observable;

/**
 * @author David Liu
 */
public interface InterestHandler {

    Observable<ChangeNotification<InstanceInfo>> forInterest(final Interest<InstanceInfo> interest);

    void shutdown();
}
