package com.netflix.eureka2.interests;

import com.netflix.eureka2.registry.instance.InstanceInfo;

/**
 * @author Nitesh Kant
 */
public class InstanceInterest extends AbstractPatternInterest<InstanceInfo> {
    protected InstanceInterest() {
    }

    public InstanceInterest(String instanceId) {
        super(instanceId);
    }

    public InstanceInterest(String instanceId, Operator operator) {
        super(instanceId, operator);
    }

    @Override
    protected String getValue(InstanceInfo data) {
        return data.getId();
    }

    @Override
    public boolean isAtomicInterest() {
        return true;
    }
}
