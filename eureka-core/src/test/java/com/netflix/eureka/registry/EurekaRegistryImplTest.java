package com.netflix.eureka.registry;

import com.netflix.eureka.interests.ChangeNotification;
import com.netflix.eureka.interests.Interests;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import rx.Observable;
import rx.Subscriber;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;


/**
 * @author David Liu
 */
public class EurekaRegistryImplTest {

    private EurekaRegistryImpl registry;

    @Rule
    public final ExternalResource registryResource = new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            registry = new EurekaRegistryImpl();
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

        Observable<InstanceInfo> snapshot = registry.snapshotForInterest(Interests.forFullRegistry());

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
                registry.forInterest(Interests.forApplication(discovery1.getApp()));

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
    public void shouldRenewLeaseWithDuration() throws Exception {

        InstanceInfo original = SampleInstanceInfo.DiscoveryServer.builder()
                .withStatus(InstanceInfo.Status.UP)
                .build();
        registry.register(original, 1);
        Thread.sleep(10);  // let time pass enough
        assertTrue(registry.hasExpired(original.getId()).toBlocking().lastOrDefault(null));

        registry.renewLease(original.getId(), 5000);
        assertFalse(registry.hasExpired(original.getId()).toBlocking().lastOrDefault(null));
    }

    @Test
    public void shouldUpdateInstanceStatus() {
        InstanceInfo original = SampleInstanceInfo.DiscoveryServer.builder()
                .withStatus(InstanceInfo.Status.UP)
                .build();
        registry.register(original, 90 * 1000).toBlocking().lastOrDefault(null);

        Lease<InstanceInfo> lease = registry.getLease(original.getId()).toBlocking().lastOrDefault(null);
        assertTrue(lease != null);
        assertEquals(InstanceInfo.Status.UP, lease.getHolder().getStatus());

        registry.updateStatus(original.getId(), InstanceInfo.Status.OUT_OF_SERVICE);
        lease = registry.getLease(original.getId()).toBlocking().lastOrDefault(null);
        assertTrue(lease != null);
        assertEquals(InstanceInfo.Status.OUT_OF_SERVICE, lease.getHolder().getStatus());
    }

    @Test
    public void shouldRegisterExistingInstanceOverwritingExisting() throws Exception {
        InstanceInfo original = SampleInstanceInfo.DiscoveryServer.builder()
                .withStatus(InstanceInfo.Status.UP)
                .build();
        registry.register(original, 90 * 1000).toBlocking().lastOrDefault(null);

        Lease<InstanceInfo> lease = registry.getLease(original.getId()).toBlocking().lastOrDefault(null);
        assertTrue(lease != null);
        assertEquals(InstanceInfo.Status.UP, lease.getHolder().getStatus());
        long lastRenewalTimestamp = lease.getLastRenewalTimestamp();
        Thread.sleep(10);  // let time pass a bit

        InstanceInfo newInstance = SampleInstanceInfo.DiscoveryServer.builder()
                .withStatus(InstanceInfo.Status.DOWN)
                .build();
        registry.register(newInstance, 90 * 1000).toBlocking().lastOrDefault(null);

        lease = registry.getLease(newInstance.getId()).toBlocking().lastOrDefault(null);
        assertTrue(lease != null);
        assertEquals(InstanceInfo.Status.DOWN, lease.getHolder().getStatus());
        assertNotEquals(lastRenewalTimestamp, lease.getLastRenewalTimestamp(), 0.00001);
    }
}
