package com.netflix.eureka.interests;

import com.netflix.eureka.registry.InstanceInfo;

/**
 * @author Nitesh Kant
 */
public class InstanceInterest extends Interest<InstanceInfo> {

    private final String instanceId;

    public InstanceInterest(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getInstanceId() {
        return instanceId;
    }

    @Override
    public boolean matches(InstanceInfo data) {
        return instanceId.equals(data.getId());
    }
}
