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

    @Override
    public String toString() {
        return "Overrides{" +
                "id='" + id + '\'' +
                ", deltas=" + deltas +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Overrides)) return false;

        Overrides overrides = (Overrides) o;

        if (deltas != null ? !deltas.equals(overrides.deltas) : overrides.deltas != null) return false;
        if (id != null ? !id.equals(overrides.id) : overrides.id != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (deltas != null ? deltas.hashCode() : 0);
        return result;
    }
}
