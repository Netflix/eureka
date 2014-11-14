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
            registry = new TestEurekaServerRegistry(new EurekaServerRegistryMetrics("serverRegistry"), testScheduler);
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

        registry.register(discovery1).toBlocking().firstOrDefault(null);
        registry.register(discovery2).toBlocking().firstOrDefault(null);
        registry.register(discovery3).toBlocking().firstOrDefault(null);

        testScheduler.triggerActions();

        Observable<InstanceInfo> snapshot = registry.forSnapshot(Interests.forFullRegistry());

        final List<InstanceInfo> returnedInstanceInfos = new ArrayList<>();
        snapshot.subscribe(new Action1<InstanceInfo>() {
            @Override
            public void call(InstanceInfo instanceInfo) {
                returnedInstanceInfos.add(instanceInfo);
                registry.register(SampleInstanceInfo.ZuulServer.build()).toBlocking().firstOrDefault(null);
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

        registry.register(discovery1).toBlocking().firstOrDefault(null);
        registry.register(discovery2).toBlocking().firstOrDefault(null);
        registry.register(discovery3).toBlocking().firstOrDefault(null);
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

        ConcurrentHashMap<String, MultiSourcedDataHolder<InstanceInfo>> internalStore = registry.getInternalStore();
        assertThat(internalStore.size(), equalTo(1));

        MultiSourcedDataHolder<InstanceInfo> holder = internalStore.values().iterator().next();
        assertThat(holder.size(), equalTo(1));
        InstanceInfo snapshot1 = holder.get();
        assertThat(snapshot1, equalTo(original));
    }

    @Test
    public void testUnregister() {
        InstanceInfo original = SampleInstanceInfo.DiscoveryServer.builder()
                .withStatus(InstanceInfo.Status.UP)
                .build();

        registry.register(original);
        testScheduler.triggerActions();

        ConcurrentHashMap<String, MultiSourcedDataHolder<InstanceInfo>> internalStore = registry.getInternalStore();
        assertThat(internalStore.size(), equalTo(1));

        MultiSourcedDataHolder<InstanceInfo> holder = internalStore.values().iterator().next();
        assertThat(holder.size(), equalTo(1));
        InstanceInfo snapshot1 = holder.get();
        assertThat(snapshot1, equalTo(original));

        registry.unregister(original);
        testScheduler.triggerActions();

        assertThat(internalStore.size(), equalTo(1));
        holder = internalStore.values().iterator().next();
        assertThat(holder.size(), equalTo(0));
        // TODO: add asserts for expiry queue
    }

    @Test
    public void testUpdate() {
        InstanceInfo original = SampleInstanceInfo.DiscoveryServer.builder()
                .withStatus(InstanceInfo.Status.UP)
                .build();

        registry.register(original);
        testScheduler.triggerActions();

        ConcurrentHashMap<String, MultiSourcedDataHolder<InstanceInfo>> internalStore = registry.getInternalStore();
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


    private static class TestEurekaServerRegistry extends EurekaServerRegistryImpl {

        public TestEurekaServerRegistry(EurekaServerRegistryMetrics metrics, Scheduler testScheduler) {
            super(metrics, testScheduler);
        }

        public ConcurrentHashMap<String, MultiSourcedDataHolder<InstanceInfo>> getInternalStore() {
            return internalStore;
        }
    }
}
