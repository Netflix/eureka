package com.netflix.eureka.registry;

import static org.junit.Assert.*;

import com.netflix.eureka.SampleInstanceInfo;
import org.junit.Test;
import rx.functions.Action1;

import java.util.Arrays;
import java.util.List;

/**
 * @author David Liu
 */
public class LeasedInstanceRegistryTest {

    @Test
    public void shouldReturnMatchingInstanceInfos() {
        InstanceInfo discovery1 = SampleInstanceInfo.DiscoveryServer.build();
        InstanceInfo discovery2 = SampleInstanceInfo.DiscoveryServer.build();
        InstanceInfo discovery3 = SampleInstanceInfo.DiscoveryServer.build();
        InstanceInfo zuul1 = SampleInstanceInfo.ZuulServer.build();

        LeasedInstanceRegistry registry = new LeasedInstanceRegistry();
        registry.register(discovery1);
        registry.register(discovery2);
        registry.register(discovery3);
        registry.register(zuul1);

        final List<String> matchingIds = Arrays.asList(discovery1.getId(), discovery2.getId(), discovery3.getId());
        registry.allMatching(Index.App, discovery1.valueForIndex(Index.App))
                .toBlocking()
                .forEach(new Action1<InstanceInfo>() {
                    @Override
                    public void call(InstanceInfo instanceInfo) {
                        assertTrue(matchingIds.contains(instanceInfo.getId()));
                    }
                });
    }

    @Test
    public void shouldRenewLeaseWithDuration() throws Exception {
        LeasedInstanceRegistry registry = new LeasedInstanceRegistry();

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
    public void shouldCancelLease() {
        LeasedInstanceRegistry registry = new LeasedInstanceRegistry();

        InstanceInfo original = SampleInstanceInfo.DiscoveryServer.builder()
                .withStatus(InstanceInfo.Status.UP)
                .build();
        registry.register(original, 90 * 1000).toBlocking().lastOrDefault(null);

        registry.cancelLease(original.getId());
        assertFalse(registry.contains(original.getId()));
    }

    @Test
    public void shouldUpdateInstanceStatus() {
        LeasedInstanceRegistry registry = new LeasedInstanceRegistry();

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
        LeasedInstanceRegistry registry = new LeasedInstanceRegistry();

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
