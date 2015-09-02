package com.netflix.eureka2.interests;

import com.netflix.eureka2.registry.instance.InstanceInfo;

/**
 * @author Tomasz Bak
 */
public class SecureVipInterest extends AbstractPatternInterest<InstanceInfo> {

    protected SecureVipInterest() {
    }

    public SecureVipInterest(String secureVip) {
        super(secureVip);
    }

    public SecureVipInterest(String secureVip, Operator operator) {
        super(secureVip, operator);
    }

    @Override
    protected String getValue(InstanceInfo data) {
        return data.getSecureVipAddress();
    }

    @Override
    public boolean isAtomicInterest() {
        return true;
    }
}
