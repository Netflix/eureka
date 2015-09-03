package com.netflix.eureka2.registry.index;

import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.model.notification.SourcedChangeNotification;
import com.netflix.eureka2.model.notification.SourcedModifyNotification;
import com.netflix.eureka2.model.notification.SourcedStreamStateNotification;
import com.netflix.eureka2.model.notification.StreamStateNotification.BufferState;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.testkit.data.builder.SampleChangeNotification;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.is;

/**
 * @author David Liu
 */
public class InstanceInfoInitStateHolderTest {
    private Source localSource = new Source(Source.Origin.LOCAL, "local");
    private Source remoteSource1 = new Source(Source.Origin.REPLICATED, "remote1", 1);
    private Source remoteSource1ng = new Source(Source.Origin.REPLICATED, "remote1", 2);
    private Source remoteSource2 = new Source(Source.Origin.REPLICATED, "remote2", 10);

    private Interest<InstanceInfo> interest = Interests.forFullRegistry();

    ChangeNotification<InstanceInfo> source1BufferStart = new SourcedStreamStateNotification<>(BufferState.BufferStart, interest, remoteSource1);
    ChangeNotification<InstanceInfo> source1BufferEnd = new SourcedStreamStateNotification<>(BufferState.BufferEnd, interest, remoteSource1);
    ChangeNotification<InstanceInfo> source1ngBufferStart = new SourcedStreamStateNotification<>(BufferState.BufferStart, interest, remoteSource1ng);
    ChangeNotification<InstanceInfo> source1ngBufferEnd = new SourcedStreamStateNotification<>(BufferState.BufferEnd, interest, remoteSource1ng);

    ChangeNotification<InstanceInfo> source2BufferStart = new SourcedStreamStateNotification<>(BufferState.BufferStart, interest, remoteSource2);
    ChangeNotification<InstanceInfo> source2BufferEnd = new SourcedStreamStateNotification<>(BufferState.BufferEnd, interest, remoteSource2);

    private ChangeNotification<InstanceInfo> cn1;
    private ChangeNotification<InstanceInfo> cn2;
    private ChangeNotification<InstanceInfo> cn3;

    private TestInstanceInfoInitStateHolder initStateHolder;
    private List<ChangeNotification<InstanceInfo>> initialRegistry;

    @Before
    public void setUp() {
        InstanceInfo.Builder seed = SampleInstanceInfo.WebServer.builder()
                .withId("asdf")
                .withApp("asdf-app");

        InstanceInfo info1 = seed.withAppGroup("foo").build();
        InstanceInfo info2 = seed.withAppGroup("bar").build();
        InstanceInfo info3 = seed.withAppGroup("baz").build();

        cn1 = new SourcedChangeNotification<>(ChangeNotification.Kind.Add, info1, localSource);
        cn2 = new SourcedChangeNotification<>(ChangeNotification.Kind.Add, info2, localSource);
        cn3 = new SourcedModifyNotification<>(info3, info3.diffOlder(info2), localSource);

        initialRegistry = Arrays.asList(
                SampleChangeNotification.CliAdd.newNotification(localSource),
                SampleChangeNotification.CliAdd.newNotification(localSource),
                SampleChangeNotification.CliAdd.newNotification(localSource),
                SampleChangeNotification.CliAdd.newNotification(localSource),
                SampleChangeNotification.CliAdd.newNotification(localSource)
        );

        interest = Interests.forFullRegistry();
        initStateHolder = new TestInstanceInfoInitStateHolder(initialRegistry.iterator(), interest);
    }

    @Test
    public void testEmpty() {
        initStateHolder = new TestInstanceInfoInitStateHolder(Collections.<ChangeNotification<InstanceInfo>>emptyIterator(), interest);
        assertThat(collect(initStateHolder), is(empty()));
    }

    @Test
    public void testInternalData() throws Exception {
        assertThat(initStateHolder.getNotificationMap().values(), containsInAnyOrder(initialRegistry.toArray()));
    }

    @Test
    public void testAddNotificationCollapse() throws Exception {
        assertThat(initStateHolder.getDataSize(), is(5));

        initStateHolder.addNotification(cn1);
        assertThat(initStateHolder.getDataSize(), is(6));
        assertThat(initStateHolder.getNotificationMap().values(), containsInAnyOrder(collapse(initialRegistry, cn1)));

        initStateHolder.addNotification(cn2);
        assertThat(initStateHolder.getDataSize(), is(6));
        assertThat(initStateHolder.getNotificationMap().values(), containsInAnyOrder(collapse(initialRegistry, cn2)));

        initStateHolder.addNotification(cn3);
        assertThat(initStateHolder.getDataSize(), is(6));
        // convert to add as the initStateHolder internally do this
        ChangeNotification<InstanceInfo> cn3AsAddNotification = new SourcedChangeNotification<>(ChangeNotification.Kind.Add, cn3.getData(), localSource);
        assertThat(initStateHolder.getNotificationMap().values(), containsInAnyOrder(collapse(initialRegistry, cn3AsAddNotification)));
    }

    @Test
    public void testSingleSourceBufferCollapse() throws Exception {
        List<ChangeNotification<InstanceInfo>> output;

        // initial bufferStart
        initStateHolder.addNotification(source1BufferStart);
        output = collect(initStateHolder);
        assertThat(output.size(), is(6));
        assertThat(output.subList(0, 1), containsInAnyOrder(source1BufferStart));
        assertThat(output.subList(1, 6), containsInAnyOrder(initialRegistry.toArray()));

        // resend bufferStart, should be merged with the initial one
        initStateHolder.addNotification(source1BufferStart);
        output = collect(initStateHolder);
        assertThat(output.size(), is(6));
        assertThat(output.subList(0, 1), containsInAnyOrder(source1BufferStart));
        assertThat(output.subList(1, 6), containsInAnyOrder(initialRegistry.toArray()));

        // send bufferEnd
        initStateHolder.addNotification(source1BufferEnd);
        output = collect(initStateHolder);
        assertThat(output.size(), is(7));
        assertThat(output.subList(0, 1), containsInAnyOrder(source1BufferStart));
        assertThat(output.subList(1, 6), containsInAnyOrder(initialRegistry.toArray()));
        assertThat(output.subList(6, 7), containsInAnyOrder(source1BufferEnd));

        // resend bufferEnd, should be merged with the initial one
        initStateHolder.addNotification(source1BufferEnd);
        output = collect(initStateHolder);
        assertThat(output.size(), is(7));
        assertThat(output.subList(0, 1), containsInAnyOrder(source1BufferStart));
        assertThat(output.subList(1, 6), containsInAnyOrder(initialRegistry.toArray()));
        assertThat(output.subList(6, 7), containsInAnyOrder(source1BufferEnd));

        // send a new bufferStart, should wipe out the bufferEnd
        initStateHolder.addNotification(source1BufferStart);
        output = collect(initStateHolder);
        assertThat(output.size(), is(6));
        assertThat(output.subList(0, 1), containsInAnyOrder(source1BufferStart));
        assertThat(output.subList(1, 6), containsInAnyOrder(initialRegistry.toArray()));
    }

    @Test
    public void testSingleSourceBufferCollapseMultipleGenerations() throws Exception {
        List<ChangeNotification<InstanceInfo>> output;

        // initial bufferStart
        initStateHolder.addNotification(source1BufferStart);
        output = collect(initStateHolder);
        assertThat(output.size(), is(6));
        assertThat(output.subList(0, 1), containsInAnyOrder(source1BufferStart));
        assertThat(output.subList(1, 6), containsInAnyOrder(initialRegistry.toArray()));

        // send bufferEnd
        initStateHolder.addNotification(source1BufferEnd);
        output = collect(initStateHolder);
        assertThat(output.size(), is(7));
        assertThat(output.subList(0, 1), containsInAnyOrder(source1BufferStart));
        assertThat(output.subList(1, 6), containsInAnyOrder(initialRegistry.toArray()));
        assertThat(output.subList(6, 7), containsInAnyOrder(source1BufferEnd));

        // send bufferEnd from a different generation, should be merged with the initial one
        initStateHolder.addNotification(source1ngBufferEnd);
        output = collect(initStateHolder);
        assertThat(output.size(), is(7));
        assertThat(output.subList(0, 1), containsInAnyOrder(source1BufferStart));
        assertThat(output.subList(1, 6), containsInAnyOrder(initialRegistry.toArray()));
        assertThat(output.subList(6, 7), containsInAnyOrder(source1ngBufferEnd));

        // send a bufferStart from a different generation, should wipe out the bufferEnd
        initStateHolder.addNotification(source1ngBufferStart);
        output = collect(initStateHolder);
        assertThat(output.size(), is(6));
        assertThat(output.subList(0, 1), containsInAnyOrder(source1ngBufferStart));
        assertThat(output.subList(1, 6), containsInAnyOrder(initialRegistry.toArray()));
    }

    @Test
    public void testMultipleSourceBufferCollapse() throws Exception {
        List<ChangeNotification<InstanceInfo>> output;

        // add 2 bufferStarts
        initStateHolder.addNotification(source1BufferStart);
        initStateHolder.addNotification(source2BufferStart);
        output = collect(initStateHolder);
        assertThat(output.size(), is(7));
        assertThat(output.subList(0, 2), containsInAnyOrder(source1BufferStart, source2BufferStart));
        assertThat(output.subList(2, 7), containsInAnyOrder(initialRegistry.toArray()));

        // add bufferEnd for 1
        initStateHolder.addNotification(source1BufferEnd);
        output = collect(initStateHolder);
        assertThat(output.size(), is(8));
        assertThat(output.subList(0, 2), containsInAnyOrder(source1BufferStart, source2BufferStart));
        assertThat(output.subList(2, 7), containsInAnyOrder(initialRegistry.toArray()));
        assertThat(output.subList(7, 8), containsInAnyOrder(source1BufferEnd));

        // add bufferEnd for 2
        initStateHolder.addNotification(source2BufferEnd);
        output = collect(initStateHolder);
        assertThat(output.size(), is(9));
        assertThat(output.subList(0, 2), containsInAnyOrder(source1BufferStart, source2BufferStart));
        assertThat(output.subList(2, 7), containsInAnyOrder(initialRegistry.toArray()));
        assertThat(output.subList(7, 9), containsInAnyOrder(source1BufferEnd, source2BufferEnd));

        // new bufferStart for 1 (wipes out the corresponding bufferEnd)
        initStateHolder.addNotification(source1ngBufferStart);
        output = collect(initStateHolder);
        assertThat(output.size(), is(8));
        assertThat(output.subList(0, 2), containsInAnyOrder(source1ngBufferStart, source2BufferStart));
        assertThat(output.subList(2, 7), containsInAnyOrder(initialRegistry.toArray()));
        assertThat(output.subList(7, 8), containsInAnyOrder(source2BufferEnd));

        // new bufferEnd for 1
        initStateHolder.addNotification(source1ngBufferEnd);
        output = collect(initStateHolder);
        assertThat(output.size(), is(9));
        assertThat(output.subList(0, 2), containsInAnyOrder(source1ngBufferStart, source2BufferStart));
        assertThat(output.subList(2, 7), containsInAnyOrder(initialRegistry.toArray()));
        assertThat(output.subList(7, 9), containsInAnyOrder(source1ngBufferEnd, source2BufferEnd));
    }


    private static List<ChangeNotification<InstanceInfo>> collect(InstanceInfoInitStateHolder initStateHolder) {
        List<ChangeNotification<InstanceInfo>> result = new ArrayList<>();
        Iterator<ChangeNotification<InstanceInfo>> iter = initStateHolder.iterator();
        while (iter.hasNext()) {
            result.add(iter.next());
        }
        return result;
    }

    @SafeVarargs
    private static ChangeNotification<InstanceInfo>[] collapse(List<ChangeNotification<InstanceInfo>> list, ChangeNotification<InstanceInfo>... individuals) {
        ChangeNotification<InstanceInfo>[] result = new ChangeNotification[list.size() + individuals.length];
        int index = 0;
        for (ChangeNotification<InstanceInfo> n : list) {
            result[index++] = n;
        }
        for (ChangeNotification<InstanceInfo> n : individuals) {
            result[index++] = n;
        }
        return result;
    }

    static class TestInstanceInfoInitStateHolder extends InstanceInfoInitStateHolder {

        public TestInstanceInfoInitStateHolder(Iterator<ChangeNotification<InstanceInfo>> initialRegistry, Interest<InstanceInfo> interest) {
            super(initialRegistry, interest);
        }

        public Map<String, ChangeNotification<InstanceInfo>> getNotificationMap() {
            return notificationMap;
        }

        public int getSize() {
            Iterator<ChangeNotification<InstanceInfo>> iter = this.iterator();
            int count = 0;
            while (iter.hasNext()) {
                iter.next();
                count++;
            }
            return count;
        }

        public int getDataSize() {
            Iterator<ChangeNotification<InstanceInfo>> iter = this.iterator();
            int count = 0;
            while (iter.hasNext()) {
                ChangeNotification<InstanceInfo> notification = iter.next();
                if (notification.isDataNotification()) {
                    count++;
                }
            }
            return count;
        }
    }
}

