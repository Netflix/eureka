package com.netflix.eureka2.client.resolver;

import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.registry.instance.InstanceInfo;

/**
 * @author David Liu
 */
public interface EurekaRemoteResolverStep {

    /**
     * optimization for common usecases
     */
    public ServerResolver forApps(String... appNames);

    /**
     * optimization for common usecases
     */
    public ServerResolver forVips(String... vipAddresses);

    public ServerResolver forInterest(Interest<InstanceInfo> interest);

}
