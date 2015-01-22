package com.netflix.eureka2.registry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import rx.Observable;
import rx.Scheduler;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;

import static com.netflix.eureka2.metric.EurekaRegistryMetricFactory.*;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;


/**
 * @author David Liu
 */
public class SourcedEurekaRegistryImplTest {

    private final TestScheduler testScheduler = Schedulers.test();
    private final Source localSource = new Source(Source.Origin.LOCAL);

    private TestEurekaServerRegistry registry;

    @Rule
    public final ExternalResource registryResource = new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            registry = new TestEurekaServerRegistry(testScheduler);
        }

        @Override
        protected void after() {
            registry.shutdown();
        }
    };

    @Test
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

    @Test
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
                registry.forInterest(Interests.forApplications(discovery1.getApp()));

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

    @Test
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

    @Test
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

    @Test
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

    @Test
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

    @Test
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
                                   Assert.fail("should never onComplete");
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

    @Test
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
                registry.forInterest(Interests.forApplications(discovery1.getApp()));

        Observable<ChangeNotification<InstanceInfo>> interestStreamAll =
                registry.forInterest(Interests.forFullRegistry());

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


    private static class TestEurekaServerRegistry extends SourcedEurekaRegistryImpl {

        TestEurekaServerRegistry(Scheduler testScheduler) {
            super(registryMetrics(), testScheduler);
        }

        public ConcurrentHashMap<String, NotifyingInstanceInfoHolder> getInternalStore() {
            return internalStore;
        }
    }
}
