package com.netflix.eureka2.testkit.data.builder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.instance.InstanceInfoBuilder;

/**
 * @author David Liu
 */
public enum SampleApp {
    Zuul {
        @Override
        public Collection<InstanceInfo> collectionOf(int n) {
            return collectionOf("Zuul", n, SampleInstanceInfo.ZuulServer.builder());
        }
    },

    Discovery {
        @Override
        public Collection<InstanceInfo> collectionOf(int n) {
            return collectionOf("Discovery", n, SampleInstanceInfo.DiscoveryServer.builder());
        }
    };

    public abstract Collection<InstanceInfo> collectionOf(int n);

    static Collection<InstanceInfo> collectionOf(String appName, int n, InstanceInfoBuilder builder) {
        List<InstanceInfo> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(randomize(appName + "_" + i, builder));
        }
        return list;
    }

    private static InstanceInfo randomize(String id, InstanceInfoBuilder builder) {
        return builder.withId(id).build();
    }

}
