package com.netflix.eureka2.server.service.overrides;

import com.netflix.eureka2.protocol.interest.DeltaDTO;
import com.netflix.eureka2.registry.instance.Delta;

import java.util.HashSet;
import java.util.Set;

/**
 * For json serialization
 *
 * @author David Liu
 */
public class OverridesDTO {

    private String id;
    private Set<DeltaDTO<?>> deltas;

    private OverridesDTO() {}  // for serializer

    OverridesDTO(String id, Set<DeltaDTO<?>> deltas) {
        this.id = id;
        this.deltas = deltas;
    }

    public String getId() {
        return id;
    }

    public Set<DeltaDTO<?>> getDeltas() {
        return deltas;
    }

    public Overrides toOverrides() {
        Set<Delta<?>> result = new HashSet<>();
        for (DeltaDTO delta : deltas) {
            result.add(delta.toDelta());
        }
        return new Overrides(id, result);
    }

    @Override
    public String toString() {
        return "OverridesDTO{" +
                "id='" + id + '\'' +
                ", deltas=" + deltas +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OverridesDTO)) return false;

        OverridesDTO that = (OverridesDTO) o;

        if (deltas != null ? !deltas.equals(that.deltas) : that.deltas != null) return false;
        if (id != null ? !id.equals(that.id) : that.id != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (deltas != null ? deltas.hashCode() : 0);
        return result;
    }

    static OverridesDTO fromOverrides(Overrides overrides) {
        Set<DeltaDTO<?>> result = new HashSet<>();
        for (Delta delta : overrides.getDeltas()) {
            result.add(DeltaDTO.toDeltaDTO(delta));
        }
        return new OverridesDTO(overrides.getId(), result);
    }
}
