package com.netflix.eureka2.server.service.overrides;

import com.netflix.eureka2.registry.instance.Delta;

import java.util.Set;

/**
 * @author David Liu
 */
public class DeltasWithId {

    private final String id;
    private final Set<Delta<?>> deltas;

    public DeltasWithId(String id, Set<Delta<?>> deltas) {
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
