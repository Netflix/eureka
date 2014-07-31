package com.netflix.eureka.protocol.registration;

/**
 * @author Tomasz Bak
 */
public class Unregister {

    public static final Unregister INSTANCE = new Unregister();

    private static final int HASH = 121298876;

    @Override
    public int hashCode() {
        return HASH;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Unregister;
    }
}
