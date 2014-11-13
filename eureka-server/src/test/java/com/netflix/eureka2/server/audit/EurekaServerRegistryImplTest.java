package com.netflix.eureka2.server.audit;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.registry.InstanceInfo;
import com.netflix.eureka2.registry.SampleInstanceInfo;
import com.netflix.eureka2.server.registry.EurekaServerRegistryImpl;
import com.netflix.eureka2.server.registry.EurekaServerRegistryMetrics;
import com.netflix.eureka2.server.registry.MultiSourcedDataHolder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import rx.Observable;
import rx.Subscriber;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;


/**
 * @author David Liu
 */
public class EurekaServerRegistryImplTest {

    private TestEurekaServerRegistry registry;

    @Rule
    public final ExternalResource registryResource = new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            registry = new TestEurekaServerRegistry(new EurekaServerRegistryMetrics("serverRegistry"));
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

        Observable<InstanceInfo> snapshot = registry.forSnapshot(Interests.forFullRegistry());

        final List<InstanceInfo> returnedInstanceInfos = new ArrayList<>();
        final CountDownLatch completionLatch = new CountDownLatch(1);

        snapshot.subscribe(new Subscriber<InstanceInfo>() {
            @Override
            public void onCompleted() {
                completionLatch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                completionLatch.countDown();
            }

            @Override
            public void onNext(InstanceInfo instanceInfo) {
                returnedInstanceInfos.add(instanceInfo);
                registry.register(SampleInstanceInfo.ZuulServer.build());
            }
        });

        registry.shutdown(); // finishes the index.

        completionLatch.await(1, TimeUnit.MINUTES);
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

        final List<String> returnedIds = new ArrayList<>();

        Observable<ChangeNotification<InstanceInfo>> interestStream =
                registry.forInterest(Interests.forApplications(discovery1.getApp()));

        final CountDownLatch completionLatch = new CountDownLatch(1);
        interestStream.subscribe(new Subscriber<ChangeNotification<InstanceInfo>>() {
            @Override
            public void onCompleted() {
                completionLatch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                completionLatch.countDown();
            }

            @Override
            public void onNext(ChangeNotification<InstanceInfo> notification) {
                returnedIds.add(notification.getData().getId());
            }
        });

        registry.shutdown(); // finishes the index.

        completionLatch.await(1, TimeUnit.MINUTES);
        assertThat(returnedIds, hasSize(3));
        assertThat(returnedIds, containsInAnyOrder(discovery1.getId(), discovery2.getId(), discovery3.getId()));
    }

    @Test
    public void testRegister() {
        InstanceInfo original = SampleInstanceInfo.DiscoveryServer.builder()
                .withStatus(InstanceInfo.Status.UP)
                .build();
        registry.register(original).toBlocking().lastOrDefault(null);

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
        registry.register(original).toBlocking().lastOrDefault(null);

        ConcurrentHashMap<String, MultiSourcedDataHolder<InstanceInfo>> internalStore = registry.getInternalStore();
        assertThat(internalStore.size(), equalTo(1));

        MultiSourcedDataHolder<InstanceInfo> holder = internalStore.values().iterator().next();
        assertThat(holder.size(), equalTo(1));
        InstanceInfo snapshot1 = holder.get();
        assertThat(snapshot1, equalTo(original));

        registry.unregister(original).toBlocking().lastOrDefault(null);

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
        registry.register(original).toBlocking().lastOrDefault(null);

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
        registry.update(newInstanceInfo, newInstanceInfo.diffOlder(original)).toBlocking().firstOrDefault(null);

        assertThat(internalStore.size(), equalTo(1));

        assertThat(holder.size(), equalTo(1));
        InstanceInfo snapshot2 = holder.get();
        assertThat(snapshot2, equalTo(newInstanceInfo));
    }


    private static class TestEurekaServerRegistry extends EurekaServerRegistryImpl {

        public TestEurekaServerRegistry(EurekaServerRegistryMetrics metrics) {
            super(metrics);
        }

        public ConcurrentHashMap<String, MultiSourcedDataHolder<InstanceInfo>> getInternalStore() {
            return internalStore;
        }
    }
}
