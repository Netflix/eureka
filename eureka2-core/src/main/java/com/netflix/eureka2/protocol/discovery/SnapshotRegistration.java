package com.netflix.eureka2.protocol.discovery;

import java.util.Arrays;

import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.registry.instance.InstanceInfo;

/**
 * Discovery protocol message representing snapshot subscription request. The {@link Interest} class hierarchy
 * which can be a composite structure of arbitrary depth is flattened prior to sending over the wire.
 *
 * @author Tomasz Bak
 */
public class SnapshotRegistration extends InterestRegistration {

    public SnapshotRegistration() {
    }

    public SnapshotRegistration(Interest<InstanceInfo> interest) {
        super(interest);
    }

    public String toString() {
        return "SnapshotRegistration{interests=" + Arrays.toString(getInterests()) + '}';
    }
}
