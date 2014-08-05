package com.netflix.eureka.interests;

import com.netflix.eureka.registry.InstanceInfo;

/**
 * @author Nitesh Kant
 */
public class FullRegistryInterest extends Interest<InstanceInfo> {

    public static final FullRegistryInterest DEFAULT_INSTANCE = new FullRegistryInterest();

    public FullRegistryInterest() {
    }

    @Override
    public boolean matches(InstanceInfo data) {
        return true;
    }
}
