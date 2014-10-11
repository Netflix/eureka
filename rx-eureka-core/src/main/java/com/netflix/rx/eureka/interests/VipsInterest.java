package com.netflix.rx.eureka.interests;

import com.netflix.rx.eureka.registry.InstanceInfo;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Nitesh Kant
 */
public class VipsInterest extends Interest<InstanceInfo> {

    private final HashSet<String> vips;

    protected VipsInterest() {
        vips = null;
    }

    public VipsInterest(String... vips) {
        this.vips = new HashSet<String>(vips.length);
        Collections.addAll(this.vips, vips);
    }

    /**
     * Returns an immutable set of vips associated with this interest.
     *
     * @return An immutable set of vips associated with this interest.
     */
    public Set<String> getVips() {
        return vips;
    }

    @Override
    public boolean matches(InstanceInfo data) {
        return vips.contains(data.getVipAddress());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        VipsInterest that = (VipsInterest) o;

        if (vips != null ? !vips.equals(that.vips) : that.vips != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return vips != null ? vips.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "VipsInterest{vips=" + vips + '}';
    }
}
