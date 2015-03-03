package com.netflix.eureka2.interests;

import com.netflix.eureka2.registry.instance.InstanceInfo;

/**
 * @author Nitesh Kant
 */
public class InstanceInterest extends AbstractPatternInterest<InstanceInfo> {
    protected InstanceInterest() {
    }

    public InstanceInterest(String secureVip) {
        super(secureVip);
    }

    public InstanceInterest(String secureVip, Operator operator) {
        super(secureVip, operator);
    }

    @Override
    protected String getValue(InstanceInfo data) {
        return data.getId();
    }
}
