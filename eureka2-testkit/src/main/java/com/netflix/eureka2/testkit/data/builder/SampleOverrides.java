package com.netflix.eureka2.testkit.data.builder;

import com.netflix.eureka2.registry.instance.Delta;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.registry.instance.InstanceInfoField;
import com.netflix.eureka2.registry.instance.ServicePort;
import com.netflix.eureka2.server.service.overrides.Overrides;
import com.netflix.eureka2.utils.ExtCollections;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author David Liu
 */
public class SampleOverrides {
    private static final List<Delta.Builder> SAMPLE_DELTA_BUILDERS = Arrays.asList(
            new Delta.Builder().withDelta(InstanceInfoField.STATUS, InstanceInfo.Status.OUT_OF_SERVICE),
            new Delta.Builder().withDelta(InstanceInfoField.ASG, "newAsg"),
            new Delta.Builder().withDelta(InstanceInfoField.PORTS, ExtCollections.asSet(new ServicePort(80, false))),
            new Delta.Builder().withDelta(InstanceInfoField.APPLICATION_GROUP, "newAppGroup")
    );

    public static Overrides generateOverrides(String id, int size) {
        Set<Delta<?>> deltas = new HashSet<>();
        for (int i = 0; i < Math.min(4, size); i++) {
            deltas.add(SAMPLE_DELTA_BUILDERS.get(i).withId(id).build());
        }
        return new Overrides(id, deltas);
    }

}
