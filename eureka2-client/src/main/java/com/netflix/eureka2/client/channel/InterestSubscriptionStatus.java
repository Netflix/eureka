package com.netflix.eureka2.client.channel;

import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.registry.instance.InstanceInfo;

/**
 * @author Tomasz Bak
 */
public interface InterestSubscriptionStatus {

    boolean isSnapshotCompleted();
    
    boolean isSnapshotCompleted(Interest<InstanceInfo> interest);
}
