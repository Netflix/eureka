package com.netflix.eureka2.registry;

/**
 * @author David Liu
 */
public class SourceMatcher {

    private static final Source LOCAL_SOURCE = new OriginSource(Source.Origin.LOCAL);
    private static final Source REPLICATED_SOURCE = new OriginSource(Source.Origin.LOCAL);
    private static final Source INTERESTED_SOURCE = new OriginSource(Source.Origin.LOCAL);

    static class OriginSource extends Source {

        private OriginSource(Origin origin) {
            super(origin, null);
        }
    }

    public static Source localSource() {
        return LOCAL_SOURCE;
    }

    public static Source replicatedSource() {
        return REPLICATED_SOURCE;
    }

    public static Source interestedSource() {
        return INTERESTED_SOURCE;
    }

    public static boolean match(Source a, Source b) {
        if (a instanceof OriginSource || b instanceof OriginSource) {
            return a.getOrigin() == b.getOrigin();
        } else {
            return a.equals(b);
        }
    }
}
