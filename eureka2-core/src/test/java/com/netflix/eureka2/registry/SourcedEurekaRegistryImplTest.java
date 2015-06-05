package com.netflix.eureka2.registry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.metric.EurekaRegistryMetricFactory;
import com.netflix.eureka2.metric.EurekaRegistryMetrics;
import com.netflix.eureka2.metric.SerializedTaskInvokerMetrics;
import com.netflix.eureka2.registry.Source.Origin;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.rx.ExtTestSubscriber;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.Scheduler;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;
import rx.subjects.BehaviorSubject;

import static com.netflix.eureka2.interests.ChangeNotifications.dataOnlyFilter;
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
public class SourcedEurekaRegistryImplTest {

    private final EurekaRegistryMetricFactory registryMetricFactory = mock(EurekaRegistryMetricFactory.class);
    private final EurekaRegistryMetrics registryMetrics = mock(EurekaRegistryMetrics.class);
    private final SerializedTaskInvokerMetrics registryTaskInvokerMetrics = mock(SerializedTaskInvokerMetrics.class);

    private final TestScheduler testScheduler = Schedulers.test();
    private final Source localSource = new Source(Source.Origin.LOCAL);
    private final Source replicatedSource = new Source(Source.Origin.REPLICATED);

    private TestEurekaServerRegistry registry;

    @Before
    public void setUp() throws Exception {
        when(registryMetricFactory.getEurekaServerRegistryMetrics()).thenReturn(registryMetrics);
        when(registryMetricFactory.getRegistryTaskInvokerMetrics()).thenReturn(registryTaskInvokerMetrics);
        registry = new TestEurekaServerRegistry(registryMetricFactory, testScheduler);
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

        registry.register(discovery1, localSource);
        registry.register(discovery2, localSource);
        registry.register(discovery3, localSource);

        testScheduler.triggerActions();

        Observable<InstanceInfo> snapshot = registry.forSnapshot(Interests.forFullRegistry());

        final List<InstanceInfo> returnedInstanceInfos = new ArrayList<>();
        snapshot.subscribe(new Action1<InstanceInfo>() {
            @Override
            public void call(InstanceInfo instanceInfo) {
                returnedInstanceInfos.add(instanceInfo);
                registry.register(SampleInstanceInfo.ZuulServer.build(), localSource);
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

        registry.register(discovery1, localSource);
        registry.register(discovery2, localSource);
        registry.register(discovery3, localSource);
        registry.register(zuul1, localSource);

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
    public void testRegisterWithObservable() throws Exception {
//        InstanceInfo original = SampleInstanceInfo.DiscoveryServer.builder().withStatus(InstanceInfo.Status.UP).build();
//        BehaviorSubject<InstanceInfo> registrationSubject = BehaviorSubject.create();
//
//        registry.register(original.getId(), localSource, registrationSubject).subscribe();
//
//        ExtTestSubscriber<Void> testSubscriber = new ExtTestSubscriber<>();
//        Observable<ChangeNotification<InstanceInfo>> interestStream =
//                registry.forInterest(Interests.forApplications(original.getApp())).filter(dataOnlyFilter())
//                .subscribe();
//
//        assertThat(testSubscriber.takeNext(10, TimeUnit.SECONDS));
    }

    @Test(timeout = 60000)
    public void testRegister() {
        InstanceInfo original = SampleInstanceInfo.DiscoveryServer.builder()
                .withStatus(InstanceInfo.Status.UP)
                .build();

        registry.register(original, localSource);
        testScheduler.triggerActions();

        ConcurrentHashMap<String, NotifyingInstanceInfoHolder> internalStore = registry.getInternalStore();
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

        registry.register(original, localSource);
        testScheduler.triggerActions();

        ConcurrentHashMap<String, NotifyingInstanceInfoHolder> internalStore = registry.getInternalStore();
        assertThat(internalStore.size(), equalTo(1));

        MultiSourcedDataHolder<InstanceInfo> holder = internalStore.values().iterator().next();
        assertThat(holder.size(), equalTo(1));
        InstanceInfo snapshot1 = holder.get();
        assertThat(snapshot1, equalTo(original));

        InstanceInfo newInstanceInfo = new InstanceInfo.Builder()
                .withInstanceInfo(original)
                .withStatus(InstanceInfo.Status.OUT_OF_SERVICE).build();

        registry.register(newInstanceInfo, localSource);
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

        registry.register(original, localSource);
        registry.register(replicated, new Source(Source.Origin.REPLICATED, "replicationSourceId"));
        testScheduler.triggerActions();

        ConcurrentHashMap<String, NotifyingInstanceInfoHolder> internalStore = registry.getInternalStore();
        assertThat(internalStore.size(), equalTo(1));

        MultiSourcedDataHolder<InstanceInfo> holder = internalStore.values().iterator().next();
        assertThat(holder.size(), equalTo(2));
        InstanceInfo snapshot1 = holder.get();
        assertThat(snapshot1, equalTo(original));

        registry.unregister(original, localSource);
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

        registry.register(original, localSource);
        testScheduler.triggerActions();

        ConcurrentHashMap<String, NotifyingInstanceInfoHolder> internalStore = registry.getInternalStore();
        assertThat(internalStore.size(), equalTo(1));

        MultiSourcedDataHolder<InstanceInfo> holder = internalStore.values().iterator().next();
        assertThat(holder.size(), equalTo(1));
        InstanceInfo snapshot1 = holder.get();
        assertThat(snapshot1, equalTo(original));

        registry.unregister(original, localSource);
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

        registry.register(original, localSource);
        testScheduler.triggerActions();

        ConcurrentHashMap<String, NotifyingInstanceInfoHolder> internalStore = registry.getInternalStore();
        assertThat(internalStore.size(), equalTo(1));

        MultiSourcedDataHolder<InstanceInfo> holder = internalStore.values().iterator().next();
        assertThat(holder.size(), equalTo(1));
        InstanceInfo snapshot = holder.get();
        assertThat(snapshot, equalTo(original));

        registry.unregister(original, localSource);
        registry.register(replicated, new Source(Source.Origin.REPLICATED, "replicationSourceId"));
        testScheduler.triggerActions();

        holder = internalStore.values().iterator().next();
        assertThat(holder.size(), equalTo(1));
        snapshot = holder.get();
        assertThat(snapshot, equalTo(replicated));

        // unregister original again, should not affect the registry
        registry.unregister(original, localSource);
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

        registry.register(original, localSource);
        testScheduler.triggerActions();

        ConcurrentHashMap<String, NotifyingInstanceInfoHolder> internalStore = registry.getInternalStore();
        assertThat(internalStore.size(), equalTo(1));

        MultiSourcedDataHolder<InstanceInfo> holder = internalStore.values().iterator().next();
        assertThat(holder.size(), equalTo(1));
        InstanceInfo snapshot1 = holder.get();
        assertThat(snapshot1, equalTo(original));

        registry.unregister(original, localSource);
        final List<ChangeNotification<InstanceInfo>> notifications = new ArrayList<>();
        registry.forInterest(Interests.forFullRegistry(), Source.matcherFor(Source.Origin.LOCAL))
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

    @Test(timeout = 60000)
    public void testUpdateWithLocalSourcePromoteOverNonLocalSnapshot() {
        InstanceInfo original = SampleInstanceInfo.DiscoveryServer.builder()
                .withStatus(InstanceInfo.Status.UP)
                .build();

        registry.register(original, replicatedSource);
        testScheduler.triggerActions();

        ConcurrentHashMap<String, NotifyingInstanceInfoHolder> internalStore = registry.getInternalStore();
        assertThat(internalStore.size(), equalTo(1));

        MultiSourcedDataHolder<InstanceInfo> holder = internalStore.values().iterator().next();
        assertThat(holder.size(), equalTo(1));
        InstanceInfo snapshot1 = holder.get();
        assertThat(snapshot1, equalTo(original));

        InstanceInfo newInstanceInfo = new InstanceInfo.Builder()
                .withInstanceInfo(original)
                .withStatus(InstanceInfo.Status.STARTING).build();

        registry.register(newInstanceInfo, localSource);
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

        registry.register(original, replicatedSource);
        testScheduler.triggerActions();

        ChangeNotification<InstanceInfo> notification = testSubscriber.takeNext();
        assertThat(notification, is(instanceOf(Sourced.class)));
        assertThat(notification.getData(), is(equalTo(original)));
        assertThat(((Sourced) notification).getSource(), is(equalTo(replicatedSource)));

        registry.register(original, localSource);  // register with the same but with localSource
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
        registry.register(original, localSource);
        testScheduler.triggerActions();

        registry.register(original, replicatedSource);
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

        registry.unregister(original, localSource);
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
        registry.register(copy1, local1);
        testScheduler.triggerActions();

        registry.register(copy2, local2);
        testScheduler.triggerActions();

        registry.register(copy3, local3);
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

        registry.unregister(copy2, local2);  // unregister the middle copy that is different from the head
        testScheduler.triggerActions();

        ChangeNotification<InstanceInfo> notification = testSubscriber.takeNext(100, TimeUnit.MILLISECONDS);
        assertThat(notification, is(nullValue()));

        registry.unregister(copy1, local1);  // unregister the head
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

        registry.register(discovery1, localSource);
        registry.register(discovery2, localSource);
        registry.register(discovery3, localSource);
        registry.register(zuul1, localSource);

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
        registry.register(first, localSource).subscribe();
        testScheduler.triggerActions();

        verify(registryMetrics, times(1)).incrementRegistrationCounter(Origin.LOCAL);
        verify(registryMetrics, times(1)).setRegistrySize(1);
        reset(registryMetrics);

        // Add second entry
        InstanceInfo second = source.next();
        registry.register(second, localSource).subscribe();
        testScheduler.triggerActions();

        verify(registryMetrics, times(1)).incrementRegistrationCounter(Origin.LOCAL);
        verify(registryMetrics, times(1)).setRegistrySize(2);
        reset(registryMetrics);

        // Remove second entry
        registry.unregister(second, localSource);
        testScheduler.triggerActions();

        verify(registryMetrics, times(1)).incrementUnregistrationCounter(Origin.LOCAL);
        verify(registryMetrics, times(1)).setRegistrySize(1);
        reset(registryMetrics);

        // Add first entry from another source
        registry.register(first, replicatedSource).subscribe();
        testScheduler.triggerActions();

        verify(registryMetrics, times(1)).incrementRegistrationCounter(Origin.REPLICATED);
        verify(registryMetrics, never()).setRegistrySize(1);
        reset(registryMetrics);

        // Remove first copy of first entry
        registry.unregister(first, localSource).subscribe();
        testScheduler.triggerActions();

        verify(registryMetrics, times(1)).incrementUnregistrationCounter(Origin.LOCAL);
        verify(registryMetrics, never()).setRegistrySize(1);
        reset(registryMetrics);

        // Remove last copy of first entry
        registry.unregister(first, replicatedSource).subscribe();
        testScheduler.triggerActions();

        verify(registryMetrics, times(1)).incrementUnregistrationCounter(Origin.REPLICATED);
        verify(registryMetrics, times(1)).setRegistrySize(0);
    }

    private static class TestEurekaServerRegistry extends SourcedEurekaRegistryImpl {

        TestEurekaServerRegistry(EurekaRegistryMetricFactory registryMetricFactory, Scheduler testScheduler) {
            super(registryMetricFactory, testScheduler);
        }

        public ConcurrentHashMap<String, NotifyingInstanceInfoHolder> getInternalStore() {
            return internalStore;
        }
    }
}
