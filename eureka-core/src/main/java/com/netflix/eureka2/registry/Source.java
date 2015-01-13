package com.netflix.eureka2.registry;

import java.util.UUID;

/**
 * A source class that must contain the origin of the source, and optionally a name. An unique id will be generated
 * for each source regardless of it's origin or name.
 *
 * @author David Liu
 */
public class Source {
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
    private final String name;  // nullable
    private final String id;

    public Source(Origin origin) {
        this(origin, null);
    }

    public Source(Origin origin, String name) {
        this.origin = origin;
        this.name = name;
        this.id = UUID.randomUUID().toString();
    }

    public Origin getOrigin() {
        return origin;
    }

    public String getName() {
        return name;
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
        if (name != null ? !name.equals(source.name) : source.name != null) return false;
        if (origin != source.origin) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = origin != null ? origin.hashCode() : 0;
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (id != null ? id.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Source{" +
                "origin=" + origin +
                ", name='" + name + '\'' +
                ", id='" + id + '\'' +
                '}';
    }

    public static Matcher matcherFor(final Source source) {
        return new Matcher() {
            @Override
            public boolean match(Source another) {
                return source.equals(another);
            }
        };
    }

    public static Matcher matcherFor(final Origin origin) {
        return new Matcher() {
            @Override
            public boolean match(Source another) {
                if (another == null) {
                    return false;
                }
                return origin.equals(another.origin);
            }
        };
    }

    public static Matcher matcherFor(final Origin origin, final String name) {
        return new Matcher() {
            @Override
            public boolean match(Source another) {
                if (another == null) {
                    return false;
                }
                return origin.equals(another.origin) && name.equals(another.name);
            }
        };
    }

    public static abstract class Matcher {
        public abstract boolean match(Source another);
    }
}
