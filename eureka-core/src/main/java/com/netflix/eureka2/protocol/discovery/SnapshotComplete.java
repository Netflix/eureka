package com.netflix.eureka2.protocol.discovery;

/**
 * @author Tomasz Bak
 */
public class SnapshotComplete {
    public static final SnapshotComplete INSTANCE = new SnapshotComplete();

    private static final int HASH = 12300872;

    @Override
    public int hashCode() {
        return HASH;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SnapshotComplete;
    }
}
