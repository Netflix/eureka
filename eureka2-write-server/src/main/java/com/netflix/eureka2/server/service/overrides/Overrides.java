package com.netflix.eureka2.server.service.overrides;

import com.netflix.eureka2.registry.instance.Delta;

import java.util.Set;

/**
 * Contains all overrides for a given id. Actual overrides are provided
 * as a set of Deltas that can be applied to instanceInfos.
 *
 * @author David Liu
 */
public class Overrides {

    private final String id;
    private final Set<Delta<?>> deltas;

    public Overrides(String id, Set<Delta<?>> deltas) {
        this.id = id;
        this.deltas = deltas;
    }

    public String getId() {
        return id;
    }

    public Set<Delta<?>> getDeltas() {
        return deltas;
    }
}
