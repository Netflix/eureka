package com.netflix.eureka2.interests;

import com.netflix.eureka2.registry.instance.InstanceInfo;

/**
 * @author Nitesh Kant
 */
public class ApplicationInterest extends AbstractPatternInterest<InstanceInfo> {
    protected ApplicationInterest() {
    }

    public ApplicationInterest(String secureVip) {
        super(secureVip);
    }

    public ApplicationInterest(String secureVip, Operator operator) {
        super(secureVip, operator);
    }

    @Override
    protected String getValue(InstanceInfo data) {
        return data.getApp();
    }
}