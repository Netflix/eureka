package com.netflix.eureka2.server.registry;

import java.util.UUID;

/**
 * @author David Liu
 */
public final class Source {
    /**
     * Each entry in a registry is associated with exactly one origin:
     * <ul>
     * <li>{@link #LOCAL}</li> - there is an opened registration client connection to the write local server
     * <li>{@link #REPLICATED}</li> - replicated entry from another server
     * <li>{@link #INTERESTED}</li> - entry from a source server specified as an interest
     * </ul>
     */
    public enum Origin { LOCAL, REPLICATED, INTERESTED }

    private final Origin origin;
    private final String id;

    private Source(Origin origin, String id) {
        this.origin = origin;
        this.id = id;
    }

    public Origin getOrigin() {
        return origin;
    }

    public String getId() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Source)) return false;

        Source source = (Source) o;

        if (id != null ? !id.equals(source.id) : source.id != null) return false;
        if (origin != source.origin) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = origin != null ? origin.hashCode() : 0;
        result = 31 * result + (id != null ? id.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Source{" +
                "origin=" + origin +
                ", id='" + id + '\'' +
                '}';
    }

    private static final Source LOCAL_SOURCE = new Source(Origin.LOCAL, UUID.randomUUID().toString());

    public static Source localSource() {
        return LOCAL_SOURCE;
    }

    public static Source replicationSource(String id) {
        return new Source(Origin.REPLICATED, id);
    }

    public static Source interestSource(String id) {
        return new Source(Origin.INTERESTED, id);
    }
}
