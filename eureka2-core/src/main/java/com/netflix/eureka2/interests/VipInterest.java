package com.netflix.eureka2.interests;

import com.netflix.eureka2.model.instance.InstanceInfo;

/**
 * @author Nitesh Kant
 */
public class VipInterest extends AbstractPatternInterest<InstanceInfo> {

    protected VipInterest() {
    }

    public VipInterest(String vip) {
        super(vip);
    }

    public VipInterest(String vip, Operator operator) {
        super(vip, operator);
    }

    @Override
    protected String getValue(InstanceInfo data) {
        return data.getVipAddress();
    }

    @Override
    public boolean isAtomicInterest() {
        return true;
    }
}
