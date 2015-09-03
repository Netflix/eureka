package com.netflix.eureka2.registry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.Sourced;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.utils.functions.ChangeNotifications;
import com.netflix.eureka2.registry.index.IndexRegistryImpl;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.model.notification.SourcedStreamStateNotification;
import com.netflix.eureka2.model.notification.StreamStateNotification;
import com.netflix.eureka2.model.notification.StreamStateNotification.BufferState;
import com.netflix.eureka2.metric.EurekaRegistryMetricFactory;
import com.netflix.eureka2.metric.EurekaRegistryMetrics;
import com.netflix.eureka2.metric.SerializedTaskInvokerMetrics;
import com.netflix.eureka2.model.Source.Origin;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.instance.InstanceInfo.Builder;
import com.netflix.eureka2.model.instance.InstanceInfo.Status;
import com.netflix.eureka2.rx.ExtTestSubscriber;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.observers.TestSubscriber;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;

import static com.netflix.eureka2.utils.functions.ChangeNotifications.dataOnlyFilter;
import static com.netflix.eureka2.testkit.junit.EurekaMatchers.addChangeNotificationOf;
import static com.netflix.eureka2.testkit.junit.EurekaMatchers.deleteChangeNotificationOf;
import static com.netflix.eureka2.testkit.junit.EurekaMatchers.modifyChangeNotificationOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


/**
 * @author David Liu
 */
public class EurekaRegistryImplTest {
    private final EurekaRegistryMetricFactory registryMetricFactory = mock(EurekaRegistryMetricFactory.class);
    private final EurekaRegistryMetrics registryMetrics = mock(EurekaRegistryMetrics.class);
    private final SerializedTaskInvokerMetrics registryTaskInvokerMetrics = mock(SerializedTaskInvokerMetrics.class);

    private final TestScheduler testScheduler = Schedulers.test();
    private final Source localSource = new Source(Source.Origin.LOCAL, "local");
    private final Source replicatedSource = new Source(Source.Origin.REPLICATED, "abc");

    private MultiSourcedDataStore<InstanceInfo> internalStore;
    private EurekaRegistry<InstanceInfo> registry;
    private ChangeNotificationObservable localDataStream;
    private ChangeNotificationObservable replicatedDataStream;

    @Before
    public void setUp() throws Exception {
        when(registryMetricFactory.getEurekaServerRegistryMetrics()).thenReturn(registryMetrics);
        when(registryMetricFactory.getRegistryTaskInvokerMetrics()).thenReturn(registryTaskInvokerMetrics);
        internalStore = new SimpleInstanceInfoDataStore(registryMetrics);
        registry = new EurekaRegistryImpl(internalStore, new IndexRegistryImpl<InstanceInfo>(), registryMetricFactory, testScheduler);
        localDataStream = ChangeNotificationObservable.create();
        replicatedDataStream = ChangeNotificationObservable.create();

        registry.connect(localSource, localDataStream).subscribe();
        registry.connect(replicatedSource, replicatedDataStream).subscribe();
    }

    @After
    public void tearDown() throws Exception {
        registry.shutdown();
    }

    @Test(timeout = 60000)
    public void shouldReturnSnapshotOfInstanceInfos() throws InterruptedException {
        InstanceInfo discovery1 = SampleInstanceInfo.DiscoveryServer.build();
        InstanceInfo discovery2 = SampleInstanceInfo.DiscoveryServer.build();
        InstanceInfo discovery3 = SampleInstanceInfo.DiscoveryServer.build();

        localDataStream.register(discovery1);
        localDataStream.register(discovery2);
        localDataStream.register(discovery3);

        testScheduler.triggerActions();

        Observable<InstanceInfo> snapshot = registry.forSnapshot(Interests.forFullRegistry());

        final List<InstanceInfo> returnedInstanceInfos = new ArrayList<>();
        snapshot.subscribe(new Action1<InstanceInfo>() {
            @Override
            public void call(InstanceInfo instanceInfo) {
                returnedInstanceInfos.add(instanceInfo);
                localDataStream.register(SampleInstanceInfo.ZuulServer.build());
            }
        });

        testScheduler.triggerActions();

        assertThat(returnedInstanceInfos.size(), greaterThanOrEqualTo(3));
        assertThat(returnedInstanceInfos, hasItems(discovery1, discovery2, discovery3));

        returnedInstanceInfos.remove(discovery1);
        returnedInstanceInfos.remove(discovery2);
        returnedInstanceInfos.remove(discovery3);

        for (InstanceInfo remaining : returnedInstanceInfos) {
            assertThat(remaining.getApp(), equalTo(SampleInstanceInfo.ZuulServer.build().getApp()));
        }
    }

    @Test(timeout = 60000)
    public void shouldReturnMatchingInstanceInfos() throws InterruptedException {
        InstanceInfo discovery1 = SampleInstanceInfo.DiscoveryServer.build();
        InstanceInfo discovery2 = SampleInstanceInfo.DiscoveryServer.build();
        InstanceInfo discovery3 = SampleInstanceInfo.DiscoveryServer.build();
        InstanceInfo zuul1 = SampleInstanceInfo.ZuulServer.build();

        localDataStream.register(discovery1);
        localDataStream.register(discovery2);
        localDataStream.register(discovery3);
        localDataStream.register(zuul1);

        testScheduler.triggerActions();

        final List<String> returnedIds = new ArrayList<>();

        Observable<ChangeNotification<InstanceInfo>> interestStream =
                registry.forInterest(Interests.forApplications(discovery1.getApp())).filter(dataOnlyFilter());

        interestStream.subscribe(new Action1<ChangeNotification<InstanceInfo>>() {
            @Override
            public void call(ChangeNotification<InstanceInfo> notification) {
                returnedIds.add(notification.getData().getId());
            }
        });

        assertThat(returnedIds.size(), is(greaterThanOrEqualTo(3)));
        assertThat(new HashSet<>(returnedIds), containsInAnyOrder(discovery1.getId(), discovery2.getId(), discovery3.getId()));
    }

    @Test
    public void testConnect() throws Exception {
        InstanceInfo original = SampleInstanceInfo.WebServer.builder().withStatus(InstanceInfo.Status.UP).build();

        // Initial add
        localDataStream.register(original);
        testScheduler.triggerActions();

        // Subscribe to interest stream and verify add notification has been sent
        ExtTestSubscriber<ChangeNotification<InstanceInfo>> interestSubscriber = new ExtTestSubscriber<>();
        registry.forInterest(Interests.forApplications(original.getApp())).filter(dataOnlyFilter())
                .subscribe(interestSubscriber);

        assertThat(interestSubscriber.takeNextOrFail(), is(addChangeNotificationOf(original)));
        assertThat(interestSubscriber.takeNext(), is(nullValue()));

        // Update instance status, and check that modify notification is issued
        InstanceInfo updated = new Builder().withInstanceInfo(original).withStatus(Status.DOWN).build();
        localDataStream.register(updated);
        testScheduler.triggerActions();

        assertThat(interestSubscriber.takeNextOrFail(), is(modifyChangeNotificationOf(updated)));
        assertThat(interestSubscriber.takeNext(), is(nullValue()));

        // Now unregister
        localDataStream.unregister(original.getId());
        testScheduler.triggerActions();

        assertThat(interestSubscriber.takeNextOrFail(), is(deleteChangeNotificationOf(updated)));
    }

    @Test(timeout = 60000)
    public void testRegister() {
        InstanceInfo original = SampleInstanceInfo.DiscoveryServer.builder()
                .withStatus(InstanceInfo.Status.UP)
                .build();

        localDataStream.register(original);
        testScheduler.triggerActions();

        assertThat(internalStore.size(), equalTo(1));

        MultiSourcedDataHolder<InstanceInfo> holder = internalStore.values().iterator().next();
        assertThat(holder.size(), equalTo(1));
        InstanceInfo snapshot1 = holder.get();
        assertThat(snapshot1, equalTo(original));
    }

    @Test(timeout = 60000)
    public void testRegisterAsUpdate() {
        InstanceInfo original = SampleInstanceInfo.DiscoveryServer.builder()
                .withStatus(InstanceInfo.Status.UP)
                .build();

        localDataStream.register(original);
        testScheduler.triggerActions();

        assertThat(internalStore.size(), equalTo(1));

        MultiSourcedDataHolder<InstanceInfo> holder = internalStore.values().iterator().next();
        assertThat(holder.size(), equalTo(1));
        InstanceInfo snapshot1 = holder.get();
        assertThat(snapshot1, equalTo(original));

        InstanceInfo newInstanceInfo = new InstanceInfo.Builder()
                .withInstanceInfo(original)
                .withStatus(InstanceInfo.Status.OUT_OF_SERVICE).build();

        localDataStream.register(newInstanceInfo);
        testScheduler.triggerActions();

        assertThat(internalStore.size(), equalTo(1));

        assertThat(holder.size(), equalTo(1));
        InstanceInfo snapshot2 = holder.get();
        assertThat(snapshot2, equalTo(newInstanceInfo));
    }

    @Test(timeout = 60000)
    public void testUnregisterWithCopiesRemaining() {
        InstanceInfo original = SampleInstanceInfo.DiscoveryServer.builder()
                .withId("sameId")
                .withStatus(InstanceInfo.Status.UP)
                .build();

        InstanceInfo replicated = SampleInstanceInfo.DiscoveryServer.builder()
                .withId("sameId")
                .withStatus(InstanceInfo.Status.OUT_OF_SERVICE)
                .build();

        localDataStream.register(original);
        replicatedDataStream.register(replicated);
        testScheduler.triggerActions();

        assertThat(internalStore.size(), equalTo(1));

        MultiSourcedDataHolder<InstanceInfo> holder = internalStore.values().iterator().next();
        assertThat(holder.size(), equalTo(2));
        InstanceInfo snapshot1 = holder.get();
        assertThat(snapshot1, equalTo(original));

        localDataStream.unregister(original.getId());
        testScheduler.triggerActions();

        assertThat(internalStore.size(), equalTo(1));
        holder = internalStore.values().iterator().next();
        assertThat(holder.size(), equalTo(1));
        assertThat(holder.get(), equalTo(replicated));
    }

    @Test(timeout = 60000)
    public void testUnregisterLastCopy() {
        InstanceInfo original = SampleInstanceInfo.DiscoveryServer.builder()
                .withId("sameId")
                .withStatus(InstanceInfo.Status.UP)
                .build();

        localDataStream.register(original);
        testScheduler.triggerActions();

        assertThat(internalStore.size(), equalTo(1));

        MultiSourcedDataHolder<InstanceInfo> holder = internalStore.values().iterator().next();
        assertThat(holder.size(), equalTo(1));
        InstanceInfo snapshot1 = holder.get();
        assertThat(snapshot1, equalTo(original));

        localDataStream.unregister(original.getId());
        testScheduler.triggerActions();

        assertThat(internalStore.size(), equalTo(0));
    }

    @Test(timeout = 60000)
    public void testUnregisterLastCopyWithNewRegistration() {
        InstanceInfo original = SampleInstanceInfo.DiscoveryServer.builder()
                .withId("sameId")
                .withStatus(InstanceInfo.Status.UP)
                .build();

        InstanceInfo replicated = SampleInstanceInfo.DiscoveryServer.builder()
                .withId("sameId")
                .withStatus(InstanceInfo.Status.DOWN)
                .build();

        localDataStream.register(original);
        testScheduler.triggerActions();

        assertThat(internalStore.size(), equalTo(1));

        MultiSourcedDataHolder<InstanceInfo> holder = internalStore.values().iterator().next();
        assertThat(holder.size(), equalTo(1));
        InstanceInfo snapshot = holder.get();
        assertThat(snapshot, equalTo(original));

        localDataStream.unregister(original.getId());
        replicatedDataStream.register(replicated);
        testScheduler.triggerActions();

        holder = internalStore.values().iterator().next();
        assertThat(holder.size(), equalTo(1));
        snapshot = holder.get();
        assertThat(snapshot, equalTo(replicated));

        // unregister original again, should not affect the registry
        localDataStream.unregister(original.getId());
        testScheduler.triggerActions();

        holder = internalStore.values().iterator().next();
        assertThat(holder.size(), equalTo(1));
        snapshot = holder.get();
        assertThat(snapshot, equalTo(replicated));
    }

    @Test(timeout = 60000)
    public void testForInterestWithSourceOnLastCopyDelete() {
        InstanceInfo original = SampleInstanceInfo.DiscoveryServer.builder()
                .withId("sameId")
                .withStatus(InstanceInfo.Status.UP)
                .build();

        localDataStream.register(original);
        testScheduler.triggerActions();

        assertThat(internalStore.size(), equalTo(1));

        MultiSourcedDataHolder<InstanceInfo> holder = internalStore.values().iterator().next();
        assertThat(holder.size(), equalTo(1));
        InstanceInfo snapshot1 = holder.get();
        assertThat(snapshot1, equalTo(original));

        localDataStream.unregister(original.getId());
        final List<ChangeNotification<InstanceInfo>> notifications = new ArrayList<>();
        registry.forInterest(Interests.forFullRegistry(), Source.matcherFor(Source.Origin.LOCAL))
                .filter(ChangeNotifications.dataOnlyFilter())
                .subscribe(new Subscriber<ChangeNotification<InstanceInfo>>() {
                    @Override
                    public void onCompleted() {
                        Assert.fail("should never onCompleted");
                    }

                    @Override
                    public void onError(Throwable e) {
                        Assert.fail("should never onError");
                    }

                    @Override
                    public void onNext(ChangeNotification<InstanceInfo> notification) {
                        notifications.add(notification);
                    }
                });

        testScheduler.triggerActions();

        assertThat(internalStore.size(), equalTo(0));
        assertThat(notifications.size(), equalTo(2));  // the initial Add plus the later Delete
        assertThat(notifications.get(0).getKind(), equalTo(ChangeNotification.Kind.Add));
        assertThat(notifications.get(0).getData(), equalTo(original));
        assertThat(notifications.get(1).getKind(), equalTo(ChangeNotification.Kind.Delete));
        assertThat(notifications.get(1).getData(), equalTo(original));
    }

    // forInterest first, then register the data (for local source, there are no live streamstate notifications)
    @Test(timeout = 30000)
    public void testForInterestOnLocalSourceStreamStateCached() throws Exception {
        InstanceInfo original = SampleInstanceInfo.DiscoveryServer.builder()
                .withStatus(InstanceInfo.Status.UP)
                .build();

        localDataStream.register(original);
        testScheduler.triggerActions();

        TestSubscriber<ChangeNotification<InstanceInfo>> subscriber = new TestSubscriber<>();
        registry.forInterest(Interests.forFullRegistry())
                .subscribe(subscriber);
        testScheduler.triggerActions();

        assertThat(internalStore.size(), equalTo(1));

        assertThat(subscriber.getOnNextEvents().size(), is(3));
        assertThat(subscriber.getOnNextEvents().get(1), addChangeNotificationOf(original));
        StreamStateNotification<InstanceInfo> bufferStart = (StreamStateNotification<InstanceInfo>) subscriber.getOnNextEvents().get(0);
        StreamStateNotification<InstanceInfo> bufferEnd = (StreamStateNotification<InstanceInfo>) subscriber.getOnNextEvents().get(2);
        assertThat(bufferStart.getBufferState(), is(BufferState.BufferStart));
        assertThat(bufferEnd.getBufferState(), is(BufferState.BufferEnd));
    }

    // forInterest first, then register the data
    @Test(timeout = 30000)
    public void testForInterestOnReplicatedSourceStreamStateOnlyLive() throws Exception {
        InstanceInfo original = SampleInstanceInfo.DiscoveryServer.builder()
                .withStatus(InstanceInfo.Status.UP)
                .build();

        ChangeNotification<InstanceInfo> bufferStart = new SourcedStreamStateNotification<>(BufferState.BufferStart, Interests.forFullRegistry(), replicatedSource);
        ChangeNotification<InstanceInfo> bufferEnd = new SourcedStreamStateNotification<>(BufferState.BufferEnd, Interests.forFullRegistry(), replicatedSource);

        TestSubscriber<ChangeNotification<InstanceInfo>> subscriber = new TestSubscriber<>();
        registry.forInterest(Interests.forFullRegistry(), Source.matcherFor(replicatedSource))
                .filter(ChangeNotifications.streamStateFilter())
                .subscribe(subscriber);
        testScheduler.triggerActions();

        replicatedDataStream.onNext(bufferStart);
        replicatedDataStream.register(original);
        replicatedDataStream.onNext(bufferEnd);
        testScheduler.triggerActions();

        assertThat(internalStore.size(), equalTo(1));

        assertThat(subscriber.getOnNextEvents().size(), is(2));
        assertThat(subscriber.getOnNextEvents().get(0), is(bufferStart));
        assertThat(subscriber.getOnNextEvents().get(1), is(bufferEnd));
    }

    // register the data, then subscriber for interest. For this case, we do not actually see any StreamStateNotifications
    // coming back, as for non-local sources we do not generate synthetic StreamStateNotifications.
    @Test(timeout = 30000)
    public void testForInterestOnReplicatedSourceStreamStateOnlyCached() throws Exception {
        InstanceInfo original = SampleInstanceInfo.DiscoveryServer.builder()
                .withStatus(InstanceInfo.Status.UP)
                .build();

        ChangeNotification<InstanceInfo> bufferStart = new SourcedStreamStateNotification<>(BufferState.BufferStart, Interests.forFullRegistry(), replicatedSource);
        ChangeNotification<InstanceInfo> bufferEnd = new SourcedStreamStateNotification<>(BufferState.BufferEnd, Interests.forFullRegistry(), replicatedSource);

        replicatedDataStream.onNext(bufferStart);
        replicatedDataStream.register(original);
        replicatedDataStream.onNext(bufferEnd);
        testScheduler.triggerActions();

        assertThat(internalStore.size(), equalTo(1));

        TestSubscriber<ChangeNotification<InstanceInfo>> subscriber = new TestSubscriber<>();
        registry.forInterest(Interests.forFullRegistry(), Source.matcherFor(replicatedSource))
                .filter(ChangeNotifications.streamStateFilter())
                .subscribe(subscriber);
        testScheduler.triggerActions();

        assertThat(subscriber.getOnNextEvents().size(), is(0));
    }

    @Test(timeout = 60000)
    public void testUpdateWithLocalSourcePromoteOverNonLocalSnapshot() {
        InstanceInfo original = SampleInstanceInfo.DiscoveryServer.builder()
                .withStatus(InstanceInfo.Status.UP)
                .build();

        replicatedDataStream.register(original);
        testScheduler.triggerActions();

        assertThat(internalStore.size(), equalTo(1));

        MultiSourcedDataHolder<InstanceInfo> holder = internalStore.values().iterator().next();
        assertThat(holder.size(), equalTo(1));
        InstanceInfo snapshot1 = holder.get();
        assertThat(snapshot1, equalTo(original));

        InstanceInfo newInstanceInfo = new InstanceInfo.Builder()
                .withInstanceInfo(original)
                .withStatus(InstanceInfo.Status.STARTING).build();

        localDataStream.register(newInstanceInfo);
        testScheduler.triggerActions();

        assertThat(internalStore.size(), equalTo(1));

        assertThat(holder.size(), equalTo(2));
        InstanceInfo snapshot2 = holder.get();
        assertThat(snapshot2, equalTo(newInstanceInfo));
    }

    @Test(timeout = 60000)
    public void testRegisterWithLocalSourcePromoteTriggersNewChangeNotification() {
        ExtTestSubscriber<ChangeNotification<InstanceInfo>> testSubscriber = new ExtTestSubscriber<>();

        registry.forInterest(Interests.forFullRegistry())
                .filter(new Func1<ChangeNotification<InstanceInfo>, Boolean>() {
                    @Override
                    public Boolean call(ChangeNotification<InstanceInfo> notification) {
                        return notification.isDataNotification();
                    }
                }).subscribe(testSubscriber);

        InstanceInfo original = SampleInstanceInfo.DiscoveryServer.builder()
                .withStatus(InstanceInfo.Status.UP)
                .build();

        replicatedDataStream.register(original);
        testScheduler.triggerActions();

        ChangeNotification<InstanceInfo> notification = testSubscriber.takeNext();
        assertThat(notification, is(instanceOf(Sourced.class)));
        assertThat(notification.getData(), is(equalTo(original)));
        assertThat(((Sourced) notification).getSource(), is(equalTo(replicatedSource)));

        localDataStream.register(original);  // register with the same but with localSource
        testScheduler.triggerActions();

        notification = testSubscriber.takeNext();
        assertThat(notification, is(instanceOf(Sourced.class)));
        assertThat(notification.getData(), is(equalTo(original)));
        assertThat(((Sourced) notification).getSource(), is(equalTo(localSource)));
    }

    @Test(timeout = 60000)
    public void testUnregisterOfLocalHeadPromoteNonLocalTriggersChangeNotifications() {
        ExtTestSubscriber<ChangeNotification<InstanceInfo>> testSubscriber = new ExtTestSubscriber<>();

        InstanceInfo original = SampleInstanceInfo.DiscoveryServer.builder()
                .withStatus(InstanceInfo.Status.UP)
                .build();

        // setup the registry data first
        localDataStream.register(original);
        testScheduler.triggerActions();

        replicatedDataStream.register(original);
        testScheduler.triggerActions();

        // subscribe to interests
        registry.forInterest(Interests.forFullRegistry())
                .filter(new Func1<ChangeNotification<InstanceInfo>, Boolean>() {
                    @Override
                    public Boolean call(ChangeNotification<InstanceInfo> notification) {
                        return notification.isDataNotification();
                    }
                }).subscribe(testSubscriber);

        ChangeNotification<InstanceInfo> initial = testSubscriber.takeNext();
        assertThat(initial, is(instanceOf(Sourced.class)));
        assertThat(initial.getData(), is(equalTo(original)));
        assertThat(((Sourced) initial).getSource(), is(equalTo(localSource)));

        localDataStream.unregister(original.getId());
        testScheduler.triggerActions();

        List<ChangeNotification<InstanceInfo>> notifications = testSubscriber.takeNext(2);

        assertThat(notifications, is(not(nullValue())));
        assertThat(notifications.size(), is(2));
        assertThat(notifications.get(0).getData(), equalTo(notifications.get(1).getData()));

        assertThat(notifications.get(0).getKind(), is(ChangeNotification.Kind.Delete));
        assertThat(notifications.get(0), is(instanceOf(Sourced.class)));
        assertThat(((Sourced) notifications.get(0)).getSource(), is(equalTo(localSource)));

        assertThat(notifications.get(1).getKind(), is(ChangeNotification.Kind.Add));
        assertThat(notifications.get(1), is(instanceOf(Sourced.class)));
        assertThat(((Sourced) notifications.get(1)).getSource(), is(equalTo(replicatedSource)));
    }


    @Test(timeout = 60000)
    public void testRegisterAndUnregisterOfMultipleLocalSourcesWithDifferentNames() throws Exception {
        Source local1 = new Source(Origin.LOCAL, "aaa");
        Source local2 = new Source(Origin.LOCAL, "bbb");
        Source local3 = new Source(Origin.LOCAL, "ccc");

        ChangeNotificationObservable local1Stream = ChangeNotificationObservable.create();
        ChangeNotificationObservable local2Stream = ChangeNotificationObservable.create();
        ChangeNotificationObservable local3Stream = ChangeNotificationObservable.create();

        registry.connect(local1, local1Stream).subscribe();
        registry.connect(local2, local2Stream).subscribe();
        registry.connect(local3, local3Stream).subscribe();

        ExtTestSubscriber<ChangeNotification<InstanceInfo>> testSubscriber = new ExtTestSubscriber<>();

        InstanceInfo copy1 = SampleInstanceInfo.DiscoveryServer.builder()
                .withStatus(InstanceInfo.Status.UP)
                .build();

        InstanceInfo copy2 = new InstanceInfo.Builder()
                .withInstanceInfo(copy1)
                .withStatus(InstanceInfo.Status.OUT_OF_SERVICE)
                .build();

        InstanceInfo copy3 = new InstanceInfo.Builder()
                .withInstanceInfo(copy1)
                .withStatus(InstanceInfo.Status.DOWN)
                .build();

        // setup the registry data first

        local1Stream.register(copy1);
        testScheduler.triggerActions();

        local2Stream.register(copy2);
        testScheduler.triggerActions();

        local3Stream.register(copy3);
        testScheduler.triggerActions();

        // subscribe to interests
        registry.forInterest(Interests.forFullRegistry())
                .filter(new Func1<ChangeNotification<InstanceInfo>, Boolean>() {
                    @Override
                    public Boolean call(ChangeNotification<InstanceInfo> notification) {
                        return notification.isDataNotification();
                    }
                }).subscribe(testSubscriber);

        ChangeNotification<InstanceInfo> initial = testSubscriber.takeNext();
        assertThat(initial, is(instanceOf(Sourced.class)));
        assertThat(initial.getData(), is(equalTo(copy1)));
        assertThat(((Sourced) initial).getSource(), is(equalTo(local1)));

        local2Stream.unregister(copy2.getId());  // unregister the middle copy that is different from the head
        testScheduler.triggerActions();

        ChangeNotification<InstanceInfo> notification = testSubscriber.takeNext(100, TimeUnit.MILLISECONDS);
        assertThat(notification, is(nullValue()));

        local1Stream.unregister(copy1.getId());  // unregister the head
        testScheduler.triggerActions();

        notification = testSubscriber.takeNext(100, TimeUnit.MILLISECONDS);
        assertThat(notification, is(instanceOf(Sourced.class)));
        assertThat(notification.getData(), is(equalTo(copy3)));
        assertThat(notification.getKind(), is(equalTo(ChangeNotification.Kind.Modify)));
        assertThat(((Sourced) notification).getSource(), is(equalTo(local3)));
    }

    @Test(timeout = 60000)
    public void testRegistryShutdownOnCompleteAllInterestStreams() throws Exception {
        InstanceInfo discovery1 = SampleInstanceInfo.DiscoveryServer.build();
        InstanceInfo discovery2 = SampleInstanceInfo.DiscoveryServer.build();
        InstanceInfo discovery3 = SampleInstanceInfo.DiscoveryServer.build();
        InstanceInfo zuul1 = SampleInstanceInfo.ZuulServer.build();

        localDataStream.register(discovery1);
        localDataStream.register(discovery2);
        localDataStream.register(discovery3);
        localDataStream.register(zuul1);

        testScheduler.triggerActions();

        final List<String> returnedIdsDiscovery = new ArrayList<>();
        final List<String> returnedIdsAll = new ArrayList<>();

        Observable<ChangeNotification<InstanceInfo>> interestStreamDiscovery =
                registry.forInterest(Interests.forApplications(discovery1.getApp())).filter(dataOnlyFilter());

        Observable<ChangeNotification<InstanceInfo>> interestStreamAll =
                registry.forInterest(Interests.forFullRegistry()).filter(dataOnlyFilter());

        final CountDownLatch discoveryStreamCompletionLatch = new CountDownLatch(1);
        Subscription discoveryStreamSubscription = interestStreamDiscovery
                .doOnCompleted(new Action0() {
                    @Override
                    public void call() {
                        discoveryStreamCompletionLatch.countDown();
                    }
                })
                .subscribe(new Action1<ChangeNotification<InstanceInfo>>() {
                    @Override
                    public void call(ChangeNotification<InstanceInfo> notification) {
                        returnedIdsDiscovery.add(notification.getData().getId());
                    }
                });

        final CountDownLatch allStreamCompletionLatch = new CountDownLatch(1);
        Subscription allStreamSubscription = interestStreamAll
                .doOnCompleted(new Action0() {
                    @Override
                    public void call() {
                        allStreamCompletionLatch.countDown();
                    }
                })
                .subscribe(new Action1<ChangeNotification<InstanceInfo>>() {
                    @Override
                    public void call(ChangeNotification<InstanceInfo> notification) {
                        returnedIdsAll.add(notification.getData().getId());
                    }
                });

        assertThat(returnedIdsDiscovery.size(), is(greaterThanOrEqualTo(3)));
        assertThat(new HashSet<>(returnedIdsDiscovery), containsInAnyOrder(discovery1.getId(), discovery2.getId(), discovery3.getId()));

        assertThat(returnedIdsAll.size(), is(greaterThanOrEqualTo(4)));
        assertThat(new HashSet<>(returnedIdsAll), containsInAnyOrder(discovery1.getId(), discovery2.getId(), discovery3.getId(), zuul1.getId()));

        assertThat(discoveryStreamSubscription.isUnsubscribed(), equalTo(false));
        assertThat(allStreamSubscription.isUnsubscribed(), equalTo(false));

        registry.shutdown();

        testScheduler.triggerActions();

        assertThat(discoveryStreamSubscription.isUnsubscribed(), equalTo(true));
        Assert.assertTrue(discoveryStreamCompletionLatch.await(30, TimeUnit.SECONDS));

        assertThat(allStreamSubscription.isUnsubscribed(), equalTo(true));
        Assert.assertTrue(allStreamCompletionLatch.await(30, TimeUnit.SECONDS));

        // shutdown again to test for idempotency and no exceptions
        registry.shutdown();
        testScheduler.triggerActions();
    }

    @Test(timeout = 60000)
    public void testMetricsCollection() throws Exception {
        Iterator<InstanceInfo> source = SampleInstanceInfo.collectionOf("test", SampleInstanceInfo.DiscoveryServer.build());

        // Add first entry
        InstanceInfo first = source.next();
        localDataStream.register(first);
        testScheduler.triggerActions();

        verify(registryMetrics, times(1)).incrementRegistrationCounter(Origin.LOCAL);
        verify(registryMetrics, times(1)).setRegistrySize(1);
        reset(registryMetrics);

        // Add second entry
        InstanceInfo second = source.next();
        localDataStream.register(second);
        testScheduler.triggerActions();

        verify(registryMetrics, times(1)).incrementRegistrationCounter(Origin.LOCAL);
        verify(registryMetrics, times(1)).setRegistrySize(2);
        reset(registryMetrics);

        // Remove second entry
        localDataStream.unregister(second.getId());
        testScheduler.triggerActions();

        verify(registryMetrics, times(1)).incrementUnregistrationCounter(Origin.LOCAL);
        verify(registryMetrics, times(1)).setRegistrySize(1);
        reset(registryMetrics);

        // Add first entry from another source
        replicatedDataStream.register(first);
        testScheduler.triggerActions();

        verify(registryMetrics, times(1)).incrementRegistrationCounter(Origin.REPLICATED);
        verify(registryMetrics, never()).setRegistrySize(1);
        reset(registryMetrics);

        // Remove first copy of first entry
        localDataStream.unregister(first.getId());
        testScheduler.triggerActions();

        verify(registryMetrics, times(1)).incrementUnregistrationCounter(Origin.LOCAL);
        verify(registryMetrics, times(1)).setRegistrySize(1);
        reset(registryMetrics);

        // Remove last copy of first entry
        replicatedDataStream.unregister(first.getId());
        testScheduler.triggerActions();

        verify(registryMetrics, times(1)).incrementUnregistrationCounter(Origin.REPLICATED);
        verify(registryMetrics, times(1)).setRegistrySize(0);
    }

    @Test
    public void testForInterestStreamStateCorrectness() {
        TestSubscriber<ChangeNotification<InstanceInfo>> testSubscriber = new TestSubscriber<>();
        registry.forInterest(Interests.forFullRegistry()).subscribe(testSubscriber);
        testScheduler.triggerActions();

        assertThat(testSubscriber.getOnNextEvents().size(), is(2));
        assertThat(testSubscriber.getOnNextEvents().get(0), is(instanceOf(StreamStateNotification.class)));
        StreamStateNotification notification = (StreamStateNotification) testSubscriber.getOnNextEvents().get(0);
        assertThat(notification.getBufferState(), is(StreamStateNotification.BufferState.BufferStart));

        assertThat(testSubscriber.getOnNextEvents().get(1), is(instanceOf(StreamStateNotification.class)));
        notification = (StreamStateNotification) testSubscriber.getOnNextEvents().get(1);
        assertThat(notification.getBufferState(), is(StreamStateNotification.BufferState.BufferEnd));

        List<InstanceInfo> instanceInfos = Arrays.asList(
                SampleInstanceInfo.ZuulServer.build(),
                SampleInstanceInfo.ZuulServer.build(),
                SampleInstanceInfo.CliServer.build()
        );

        localDataStream.register(instanceInfos.get(0));
        localDataStream.register(instanceInfos.get(1));
        localDataStream.register(instanceInfos.get(2));
        testScheduler.triggerActions();

        assertThat(internalStore.size(), equalTo(3));

        testSubscriber = new TestSubscriber<>();
        registry.forInterest(Interests.forFullRegistry()).subscribe(testSubscriber);
        testScheduler.triggerActions();

        assertThat(testSubscriber.getOnNextEvents().size(), is(5));
        assertThat(testSubscriber.getOnNextEvents().get(0), is(instanceOf(StreamStateNotification.class)));
        notification = (StreamStateNotification) testSubscriber.getOnNextEvents().get(0);
        assertThat(notification.getBufferState(), is(StreamStateNotification.BufferState.BufferStart));

        assertThat(testSubscriber.getOnNextEvents().get(4), is(instanceOf(StreamStateNotification.class)));
        notification = (StreamStateNotification) testSubscriber.getOnNextEvents().get(4);
        assertThat(notification.getBufferState(), is(StreamStateNotification.BufferState.BufferEnd));

        testSubscriber = new TestSubscriber<>();
        registry.forInterest(Interests.forApplications(instanceInfos.get(0).getApp())).subscribe(testSubscriber);
        testScheduler.triggerActions();

        assertThat(testSubscriber.getOnNextEvents().size(), is(4));
        assertThat(testSubscriber.getOnNextEvents().get(0), is(instanceOf(StreamStateNotification.class)));
        notification = (StreamStateNotification) testSubscriber.getOnNextEvents().get(0);
        assertThat(notification.getBufferState(), is(StreamStateNotification.BufferState.BufferStart));

        assertThat(testSubscriber.getOnNextEvents().get(3), is(instanceOf(StreamStateNotification.class)));
        notification = (StreamStateNotification) testSubscriber.getOnNextEvents().get(3);
        assertThat(notification.getBufferState(), is(StreamStateNotification.BufferState.BufferEnd));
    }

    @Test(timeout = 30000)
    public void testEvictAll() throws Exception {
        Iterator<InstanceInfo> source = SampleInstanceInfo.collectionOf("test", SampleInstanceInfo.DiscoveryServer.build());
        InstanceInfo a = source.next();
        InstanceInfo b = source.next();
        InstanceInfo aCopy = new Builder().withInstanceInfo(a).withAppGroup("differentA").build();
        InstanceInfo bCopy = new Builder().withInstanceInfo(b).withAppGroup("differentB").build();

        localDataStream.register(a);
        localDataStream.register(b);
        replicatedDataStream.register(aCopy);
        replicatedDataStream.register(bCopy);
        testScheduler.triggerActions();

        Collection<MultiSourcedDataHolder<InstanceInfo>> holders = internalStore.values();
        assertThat(holders.size(), is(2));
        for (MultiSourcedDataHolder<InstanceInfo> holder : holders) {
            assertThat(holder.size(), is(2));  // 2 copies each
        }
    }
}
