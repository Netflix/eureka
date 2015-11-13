package com.netflix.eureka2.registry;

import java.util.Collection;

import com.netflix.eureka2.model.StdSource;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.registry.MultiSourcedInstanceInfoHolder.HolderStore;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

/**
 * Have a dedicated test class for this inner class within NotifyingInstanceInfoHolder
 * as it does some pretty critical (if straight forward) business logic.
 *
 * @author David Liu
 */
public class MultiSourcedInstanceInfoHolderHolderStoreTest {

    private final HolderStore dataStore = new HolderStore();

    private final StdSource source1 = new StdSource(StdSource.Origin.REPLICATED, "replicationSourceId");
    private final StdSource source2 = new StdSource(StdSource.Origin.LOCAL);

    private final InstanceInfo info1 = SampleInstanceInfo.DiscoveryServer.build();
    private final InstanceInfo info2 = SampleInstanceInfo.ZuulServer.build();

    @Before
    public void setUp() {
        dataStore.put(source1, info1);
        dataStore.put(source2, info2);

        assertThat(dataStore.sourceMap.size(), is(2));
        assertThat(dataStore.sourceMap.values(), containsInAnyOrder(source1, source2));

        assertThat(dataStore.dataMap.size(), is(2));
        assertThat(dataStore.dataMap.keySet(), containsInAnyOrder(source1, source2));
        assertThat(dataStore.dataMap.values(), containsInAnyOrder(info1, info2));
    }

    @Test
    public void testPut() {
        // add for same source
        InstanceInfo newInfo1 = SampleInstanceInfo.CliServer.build();
        dataStore.put(source1, newInfo1);

        verifySourceMapAndDataMapInSync();
        assertThat(dataStore.dataMap.values(), containsInAnyOrder(newInfo1, info2));

        // add a new source
        StdSource source3 = new StdSource(StdSource.Origin.REPLICATED, "anotherReplicationSourceId");
        InstanceInfo info3 = SampleInstanceInfo.EurekaReadServer.build();
        dataStore.put(source3, info3);

        verifySourceMapAndDataMapInSync();
        assertThat(dataStore.sourceMap.values(), containsInAnyOrder(source1, source2, source3));
        assertThat(dataStore.dataMap.values(), containsInAnyOrder(newInfo1, info2, info3));

        // add for same source with different id
        StdSource newSource2 = fromAnother(source2);
        InstanceInfo newInfo2 = SampleInstanceInfo.EurekaWriteServer.build();
        assertThat(source2, is(not(newSource2)));
        dataStore.put(newSource2, newInfo2);

        verifySourceMapAndDataMapInSync();
        assertThat(dataStore.sourceMap.values(), containsInAnyOrder(source1, newSource2, source3));
        assertThat(dataStore.dataMap.values(), containsInAnyOrder(newInfo1, newInfo2, info3));
    }

    @Test
    public void testRemove() {
        InstanceInfo removed;

        // remove for non-existing source
        StdSource source3 = new StdSource(StdSource.Origin.REPLICATED, "anotherReplicationSourceId");
        removed = dataStore.remove(source3);
        assertThat(removed, is(nullValue()));

        // remove for existing sources with different id
        StdSource newSource1 = fromAnother(source1);
        removed = dataStore.remove(newSource1);
        assertThat(removed, is(nullValue()));

        StdSource newSource2 = fromAnother(source2);
        removed = dataStore.remove(newSource2);
        assertThat(removed, is(nullValue()));

        verifySourceMapAndDataMapInSync();
        assertThat(dataStore.sourceMap.size(), is(2));
        assertThat(dataStore.dataMap.size(), is(2));

        // remove for existing sources
        removed = dataStore.remove(source1);
        assertThat(removed, is(info1));

        removed = dataStore.remove(source2);
        assertThat(removed, is(info2));

        verifySourceMapAndDataMapInSync();
        assertThat(dataStore.sourceMap.size(), is(0));
        assertThat(dataStore.dataMap.size(), is(0));
    }

    @Test
    public void testGetMatching() {
        InstanceInfo instanceInfo;
        // get with non-existent source
        StdSource source3 = new StdSource(StdSource.Origin.REPLICATED, "anotherReplicationSourceId");
        instanceInfo = dataStore.getMatching(source3);
        assertThat(instanceInfo, is(nullValue()));

        // get with a source that matches an existing source's origin:name but have diff id
        StdSource newSource1 = fromAnother(source1);
        instanceInfo = dataStore.getMatching(newSource1);
        assertThat(instanceInfo, is(info1));

        StdSource newSource2 = fromAnother(source2);
        instanceInfo = dataStore.getMatching(newSource2);
        assertThat(instanceInfo, is(info2));

        verifySourceMapAndDataMapInSync();
        assertThat(dataStore.sourceMap.size(), is(2));
        assertThat(dataStore.dataMap.size(), is(2));

        // get with an existing source
        instanceInfo = dataStore.getMatching(source1);
        assertThat(instanceInfo, is(info1));

        instanceInfo = dataStore.getMatching(source2);
        assertThat(instanceInfo, is(info2));

        verifySourceMapAndDataMapInSync();
        assertThat(dataStore.sourceMap.size(), is(2));
        assertThat(dataStore.dataMap.size(), is(2));
    }

    @Test
    public void testGetExact() {
        InstanceInfo instanceInfo;
        // get with non-existent source
        StdSource source3 = new StdSource(StdSource.Origin.REPLICATED, "anotherReplicationSourceId");
        instanceInfo = dataStore.getExact(source3);
        assertThat(instanceInfo, is(nullValue()));

        // get with a source that matches an existing source's origin:name but have diff id
        StdSource newSource1 = fromAnother(source1);
        instanceInfo = dataStore.getExact(newSource1);
        assertThat(instanceInfo, is(nullValue()));

        StdSource newSource2 = fromAnother(source2);
        instanceInfo = dataStore.getExact(newSource2);
        assertThat(instanceInfo, is(nullValue()));

        verifySourceMapAndDataMapInSync();
        assertThat(dataStore.sourceMap.size(), is(2));
        assertThat(dataStore.dataMap.size(), is(2));

        // get with an existing source
        instanceInfo = dataStore.getExact(source1);
        assertThat(instanceInfo, is(info1));

        instanceInfo = dataStore.getExact(source2);
        assertThat(instanceInfo, is(info2));

        verifySourceMapAndDataMapInSync();
        assertThat(dataStore.sourceMap.size(), is(2));
        assertThat(dataStore.dataMap.size(), is(2));
    }

    private void verifySourceMapAndDataMapInSync() {
        Collection<Source> fromSourceMap = dataStore.sourceMap.values();
        Collection<Source> fromDataMap = dataStore.dataMap.keySet();
        assertThat(fromSourceMap, containsInAnyOrder(fromDataMap.toArray()));
    }

    // create a source from another with the same origin and name (but diff id as the id is created internally)
    private StdSource fromAnother(StdSource source) {
        return new StdSource(source.getOrigin(), source.getName());
    }
}
