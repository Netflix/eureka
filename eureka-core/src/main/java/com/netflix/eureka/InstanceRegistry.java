package com.netflix.eureka;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.discovery.shared.LookupService;
import com.netflix.eureka.lease.LeaseManager;

/**
 * @author Tomasz Bak
 */
public interface InstanceRegistry extends LeaseManager<InstanceInfo>, LookupService<String> {

    void storeOverriddenStatusIfRequired(String id, InstanceStatus overriddenStatus);

}
