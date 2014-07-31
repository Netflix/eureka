package com.netflix.eureka.protocol;

/**
 * We assume that heart beats are at the application level, not embedded
 * into the transport layer/message broker.
 *
 * @author Tomasz Bak
 */
public class Heartbeat {
    public static final Heartbeat INSTANCE = new Heartbeat();

    private static final int HASH = 98656312;

    @Override
    public int hashCode() {
        return HASH;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Heartbeat;
    }
}
