package com.netflix.eureka2.interests;

import com.netflix.eureka2.model.instance.InstanceInfo;

/**
 * @author David Liu
 */
public class EmptyRegistryInterest implements Interest<InstanceInfo> {

    private static final EmptyRegistryInterest DEFAULT_INSTANCE = new EmptyRegistryInterest();

    private static final int HASH = 234234128;

    public static EmptyRegistryInterest getInstance() {
        return DEFAULT_INSTANCE;
    }

    @Override
    public boolean matches(InstanceInfo data) {
        return false;
    }

    @Override
    public boolean isAtomicInterest() {
        return true;
    }

    @Override
    public int hashCode() {
        return HASH;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof EmptyRegistryInterest;
    }
}
