package com.netflix.eureka.protocol.discovery;

/**
 * @author Tomasz Bak
 */
public class UnregisterInterestSet {

    public static final UnregisterInterestSet INSTANCE = new UnregisterInterestSet();

    private static final int HASH = 432412432;

    @Override
    public int hashCode() {
        return HASH;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof UnregisterInterestSet;
    }
}
