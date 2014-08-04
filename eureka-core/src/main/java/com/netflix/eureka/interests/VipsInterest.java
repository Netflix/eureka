package com.netflix.eureka.interests;

import com.netflix.eureka.registry.InstanceInfo;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Nitesh Kant
 */
public class VipsInterest extends Interest<InstanceInfo> {

    private final Set<String> vips;

    public VipsInterest(String... vips) {
        this.vips = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(vips)));
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
}
