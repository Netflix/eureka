package com.netflix.eureka.registry;

import static org.junit.Assert.*;

import org.junit.Test;
import rx.functions.Action1;

import java.util.Arrays;
import java.util.List;

/**
 * @author David Liu
 */
public class InstanceRegistryTest {

    @Test
    public void shouldReturnMatchingInstanceInfos() {
        InstanceInfo info1 = new InstanceInfo.Builder()
                .withId("1").withApp("APP-1")
                .build();
        InstanceInfo info2 = new InstanceInfo.Builder()
                .withId("2").withApp("APP-1")
                .build();
        InstanceInfo info3 = new InstanceInfo.Builder()
                .withId("3").withApp("APP-1")
                .build();
        InstanceInfo info4 = new InstanceInfo.Builder()
                .withId("4").withApp("APP-2")
                .build();

        InstanceRegistry registry = new InstanceRegistry();
        registry.add(info1).subscribe();
        registry.add(info2).subscribe();
        registry.add(info3).subscribe();
        registry.add(info4).subscribe();

        final List<String> matchingIds = Arrays.asList("1","2","3");
        registry.allMatching(Index.App, "APP-1").subscribe(new Action1<InstanceInfo>() {
            @Override
            public void call(InstanceInfo instanceInfo) {
                assertTrue(matchingIds.contains(instanceInfo.getId()));
            }
        });
    }
}
