package com.netflix.eureka2.utils.functions;

import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.model.notification.ChangeNotification.Kind;
import com.netflix.eureka2.registry.index.IndexRegistryImpl;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.model.notification.SourcedStreamStateNotification;
import com.netflix.eureka2.model.notification.StreamStateNotification.BufferState;
import com.netflix.eureka2.metric.EurekaRegistryMetricFactory;
import com.netflix.eureka2.protocol.common.AddInstance;
import com.netflix.eureka2.protocol.common.DeleteInstance;
import com.netflix.eureka2.protocol.interest.UpdateInstanceInfo;
import com.netflix.eureka2.registry.ChangeNotificationObservable;
import com.netflix.eureka2.registry.EurekaRegistry;
import com.netflix.eureka2.registry.EurekaRegistryImpl;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.Sourced;
import com.netflix.eureka2.registry.MultiSourcedDataHolder;
import com.netflix.eureka2.registry.MultiSourcedDataStore;
import com.netflix.eureka2.registry.SimpleInstanceInfoDataStore;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.helpers.NOPLogger;
import rx.observers.TestSubscriber;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author David Liu
 */
public class ChannelFunctionsTest {
    private final Logger logger = NOPLogger.NOP_LOGGER;
    private final TestScheduler testScheduler = Schedulers.test();

    private ChannelFunctions channelFunctions;
    private Iterator<InstanceInfo> infoIterator;
    @Before
    public void setUp() {
        channelFunctions = new ChannelFunctions(logger);
        infoIterator = SampleInstanceInfo.collectionOf("test", SampleInstanceInfo.WebServer.build());
    }

    @Test
    public void testChannelMessageToNotification() {
        ChangeNotification<InstanceInfo> notification;
        Map<String, InstanceInfo> cache = new HashMap<>();

        Source localSource = new Source(Source.Origin.LOCAL, "local");
        Source remoteSource = new Source(Source.Origin.REPLICATED, "remote");

        InstanceInfo a = infoIterator.next();
        notification = channelFunctions.channelMessageToNotification(new AddInstance(a), localSource, cache);
        assertThat(notification.getKind(), is(Kind.Add));
        assertThat(notification.getData(), is(a));
        assertThat(((Sourced)notification).getSource(), is(localSource));
        assertThat(cache.size(), is(1));
        assertThat(cache.get(a.getId()), is(a));

        InstanceInfo aNew = new InstanceInfo.Builder().withInstanceInfo(a).withStatus(InstanceInfo.Status.OUT_OF_SERVICE).build();
        notification = channelFunctions.channelMessageToNotification(new UpdateInstanceInfo(aNew.diffOlder(a).iterator().next()), remoteSource, cache);
        assertThat(notification.getKind(), is(Kind.Modify));
        assertThat(notification.getData(), is(aNew));
        assertThat(((Sourced)notification).getSource(), is(remoteSource));
        assertThat(cache.size(), is(1));
        assertThat(cache.get(a.getId()), is(aNew));

        notification = channelFunctions.channelMessageToNotification(new DeleteInstance(a.getId()), localSource, cache);
        assertThat(notification.getKind(), is(Kind.Delete));
        assertThat(notification.getData(), is(aNew));
        assertThat(((Sourced)notification).getSource(), is(localSource));
        assertThat(cache.size(), is(0));
    }

    @Test
    public void testEvictionSetup() {
        MultiSourcedDataStore<InstanceInfo> dataStore = new SimpleInstanceInfoDataStore(
                EurekaRegistryMetricFactory.registryMetrics().getEurekaServerRegistryMetrics());
        EurekaRegistry<InstanceInfo> registry = spy(new EurekaRegistryImpl(
                dataStore, new IndexRegistryImpl<InstanceInfo>(), EurekaRegistryMetricFactory.registryMetrics(), testScheduler));

        // first add some data to the registry from an older source
        Source prevSource = new Source(Source.Origin.REPLICATED, "abc", 1);
        ChangeNotificationObservable dataStream = ChangeNotificationObservable.create();
        registry.connect(prevSource, dataStream).subscribe();

        InstanceInfo a = infoIterator.next();
        InstanceInfo b = infoIterator.next();
        InstanceInfo aNew = new InstanceInfo.Builder().withInstanceInfo(a).withVipAddress("newA").build();

        dataStream.register(a);
        dataStream.register(b);
        testScheduler.triggerActions();

        assertThat(dataStore.size(), is(2));
        for (MultiSourcedDataHolder<InstanceInfo> holder : dataStore.values()) {
            assertThat(holder.size(), is(1));
            assertThat(holder.getSource(), is(prevSource));
        }

        // now setup the eviction
        Source currSource = new Source(Source.Origin.REPLICATED, "abc", 2);
        TestSubscriber<Void> evictionSubscriber = new TestSubscriber<>();
        channelFunctions.setUpPrevChannelEviction(currSource, registry).subscribe(evictionSubscriber);

        // verify that eviction is not called (as we have not seen a streamStateNotification yet)
        evictionSubscriber.awaitTerminalEvent(200, TimeUnit.MILLISECONDS);
        assertThat(evictionSubscriber.getOnCompletedEvents(), is(empty()));
        verify(registry, never()).evictAll(any(Source.SourceMatcher.class));

        // now resend the same data from the new source with buffer markers
        ChangeNotificationObservable dataStream2 = ChangeNotificationObservable.create();
        registry.connect(currSource, dataStream2).subscribe();

        dataStream2.onNext(new SourcedStreamStateNotification<>(BufferState.BufferStart, Interests.forFullRegistry(), currSource));
        dataStream2.register(aNew);
        testScheduler.triggerActions();

        assertThat(dataStore.size(), is(2));
        for (MultiSourcedDataHolder<InstanceInfo> holder : dataStore.values()) {
            assertThat(holder.size(), is(1));
            if (holder.getSource().equals(prevSource)) {
                assertThat(holder.get(), is(b));  // b was not overridden
            } else if (holder.getSource().equals(currSource)) {
                assertThat(holder.get(), is(aNew));  // a was overridden
            } else {
                Assert.fail("Should not be here");
            }
        }

        // verify that eviction is not called (as we have not seen a bufferEnd yet)
        evictionSubscriber.awaitTerminalEvent(200, TimeUnit.MILLISECONDS);
        assertThat(evictionSubscriber.getOnCompletedEvents(), is(empty()));
        verify(registry, never()).evictAll(any(Source.SourceMatcher.class));

        // send the bufferEnd, should trigger eviction of b
        dataStream2.onNext(new SourcedStreamStateNotification<>(BufferState.BufferEnd, Interests.forFullRegistry(), currSource));
        testScheduler.triggerActions();

        assertThat(dataStore.size(), is(1));

        for (MultiSourcedDataHolder<InstanceInfo> holder : dataStore.values()) {
            assertThat(holder.size(), is(1));
            assertThat(holder.getSource(), is(currSource));
            assertThat(holder.get(), is(aNew));  // a was overridden so should not be evicted
        }

        evictionSubscriber.awaitTerminalEvent(200, TimeUnit.MILLISECONDS);
        assertThat(evictionSubscriber.getOnCompletedEvents().size(), is(1));
        verify(registry, times(1)).evictAll(any(Source.SourceMatcher.class));
    }

}
