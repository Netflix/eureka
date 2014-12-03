package com.netflix.eureka2.server.registry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.registry.InstanceInfo;
import com.netflix.eureka2.registry.SampleInstanceInfo;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import rx.Observable;
import rx.Scheduler;
import rx.functions.Action1;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;

import static com.netflix.eureka2.server.metric.EurekaServerMetricFactory.*;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;


/**
 * @author David Liu
 */
public class EurekaServerRegistryImplTest {

    private final TestScheduler testScheduler = Schedulers.test();
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

        registry.register(discovery1);
        registry.register(discovery2);
        registry.register(discovery3);

        testScheduler.triggerActions();

        Observable<InstanceInfo> snapshot = registry.forSnapshot(Interests.forFullRegistry());

        final List<InstanceInfo> returnedInstanceInfos = new ArrayList<>();
        snapshot.subscribe(new Action1<InstanceInfo>() {
            @Override
            public void call(InstanceInfo instanceInfo) {
                returnedInstanceInfos.add(instanceInfo);
                registry.register(SampleInstanceInfo.ZuulServer.build());
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

        registry.register(discovery1);
        registry.register(discovery2);
        registry.register(discovery3);
        registry.register(zuul1);

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

        registry.register(original);
        testScheduler.triggerActions();

        ConcurrentHashMap<String, NotifyingInstanceInfoHolder> internalStore = registry.getInternalStore();
        assertThat(internalStore.size(), equalTo(1));

        MultiSourcedDataHolder<InstanceInfo> holder = internalStore.values().iterator().next();
        assertThat(holder.size(), equalTo(1));
        InstanceInfo snapshot1 = holder.get();
        assertThat(snapshot1, equalTo(original));
    }

    @Test
    public void testUpdate() {
        InstanceInfo original = SampleInstanceInfo.DiscoveryServer.builder()
                .withStatus(InstanceInfo.Status.UP)
                .build();

        registry.register(original);
        testScheduler.triggerActions();

        ConcurrentHashMap<String, NotifyingInstanceInfoHolder> internalStore = registry.getInternalStore();
        assertThat(internalStore.size(), equalTo(1));

        MultiSourcedDataHolder<InstanceInfo> holder = internalStore.values().iterator().next();
        assertThat(holder.size(), equalTo(1));
        InstanceInfo snapshot1 = holder.get();
        assertThat(snapshot1, equalTo(original));

        InstanceInfo newInstanceInfo = new InstanceInfo.Builder()
                .withInstanceInfo(original)
                .withVersion(1L)
                .withStatus(InstanceInfo.Status.OUT_OF_SERVICE).build();

        registry.update(newInstanceInfo, newInstanceInfo.diffOlder(original));
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

        registry.register(original);
        registry.register(replicated, Source.replicationSource("replicationSourceId"));
        testScheduler.triggerActions();

        ConcurrentHashMap<String, NotifyingInstanceInfoHolder> internalStore = registry.getInternalStore();
        assertThat(internalStore.size(), equalTo(1));

        MultiSourcedDataHolder<InstanceInfo> holder = internalStore.values().iterator().next();
        assertThat(holder.size(), equalTo(2));
        InstanceInfo snapshot1 = holder.get();
        assertThat(snapshot1, equalTo(original));

        registry.unregister(original);
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

        registry.register(original);
        testScheduler.triggerActions();

        ConcurrentHashMap<String, NotifyingInstanceInfoHolder> internalStore = registry.getInternalStore();
        assertThat(internalStore.size(), equalTo(1));

        MultiSourcedDataHolder<InstanceInfo> holder = internalStore.values().iterator().next();
        assertThat(holder.size(), equalTo(1));
        InstanceInfo snapshot1 = holder.get();
        assertThat(snapshot1, equalTo(original));

        registry.unregister(original);
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

        registry.register(original);
        testScheduler.triggerActions();

        ConcurrentHashMap<String, NotifyingInstanceInfoHolder> internalStore = registry.getInternalStore();
        assertThat(internalStore.size(), equalTo(1));

        MultiSourcedDataHolder<InstanceInfo> holder = internalStore.values().iterator().next();
        assertThat(holder.size(), equalTo(1));
        InstanceInfo snapshot = holder.get();
        assertThat(snapshot, equalTo(original));

        registry.unregister(original);
        registry.register(replicated, Source.replicationSource("replicationSourceId"));
        testScheduler.triggerActions();

        holder = internalStore.values().iterator().next();
        assertThat(holder.size(), equalTo(1));
        snapshot = holder.get();
        assertThat(snapshot, equalTo(replicated));

        // unregister original again, should not affect the registry
        registry.unregister(original);
        testScheduler.triggerActions();

        holder = internalStore.values().iterator().next();
        assertThat(holder.size(), equalTo(1));
        snapshot = holder.get();
        assertThat(snapshot, equalTo(replicated));
    }



    private static class TestEurekaServerRegistry extends EurekaServerRegistryImpl {

        TestEurekaServerRegistry(Scheduler testScheduler) {
            super(serverMetrics(), testScheduler);
        }

        public ConcurrentHashMap<String, NotifyingInstanceInfoHolder> getInternalStore() {
            return internalStore;
        }
    }
}
