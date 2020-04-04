package com.netflix.discovery;

import com.netflix.appinfo.DataCenterInfo;
import com.netflix.appinfo.HealthCheckHandler;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.LeaseInfo;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author David Liu
 */
public class InstanceInfoReplicatorTest {

    private final int burstSize = 2;
    private final int refreshRateSeconds = 2;

    private DiscoveryClient discoveryClient;
    private InstanceInfoReplicator replicator;

    @Before
    public void setUp() throws Exception {
        discoveryClient = mock(DiscoveryClient.class);

        InstanceInfo.Builder builder = InstanceInfo.Builder.newBuilder()
                .setIPAddr("10.10.101.00")
                .setHostName("Hosttt")
                .setAppName("EurekaTestApp-" + UUID.randomUUID())
                .setDataCenterInfo(new DataCenterInfo() {
                    @Override
                    public Name getName() {
                        return Name.MyOwn;
                    }
                })
                .setLeaseInfo(LeaseInfo.Builder.newBuilder().setRenewalIntervalInSecs(30).build());

        InstanceInfo instanceInfo = builder.build();
        instanceInfo.setStatus(InstanceInfo.InstanceStatus.DOWN);

        this.replicator = new InstanceInfoReplicator(discoveryClient, instanceInfo, refreshRateSeconds, burstSize);
    }

    @After
    public void tearDown() throws Exception {
        replicator.stop();
    }

    @Test
    public void testOnDemandUpdate() throws Throwable {
        assertTrue(replicator.onDemandUpdate());
        Thread.sleep(10);  // give some time for execution
        assertTrue(replicator.onDemandUpdate());
        Thread.sleep(1000 * refreshRateSeconds / 2);
        assertTrue(replicator.onDemandUpdate());
        Thread.sleep(10);

        verify(discoveryClient, times(3)).refreshInstanceInfo();
        verify(discoveryClient, times(1)).register();
    }

    @Test
    public void testOnDemandUpdateRateLimiting() throws Throwable {
        assertTrue(replicator.onDemandUpdate());
        Thread.sleep(10);  // give some time for execution
        assertTrue(replicator.onDemandUpdate());
        Thread.sleep(10);  // give some time for execution
        assertFalse(replicator.onDemandUpdate());
        Thread.sleep(10);

        verify(discoveryClient, times(2)).refreshInstanceInfo();
        verify(discoveryClient, times(1)).register();
    }

    @Test
    public void testOnDemandUpdateResetAutomaticRefresh() throws Throwable {
        replicator.start(0);
        Thread.sleep(1000 * refreshRateSeconds / 2);

        assertTrue(replicator.onDemandUpdate());

        Thread.sleep(1000 * refreshRateSeconds + 50);
        verify(discoveryClient, times(3)).refreshInstanceInfo(); // 1 initial refresh, 1 onDemand, 1 auto
        verify(discoveryClient, times(1)).register();  // all but 1 is no-op
    }

    @Test
    public void testOnDemandUpdateResetAutomaticRefreshWithInitialDelay() throws Throwable {
        replicator.start(1000 * refreshRateSeconds);

        assertTrue(replicator.onDemandUpdate());

        Thread.sleep(1000 * refreshRateSeconds + 100);
        verify(discoveryClient, times(2)).refreshInstanceInfo(); // 1 onDemand, 1 auto
        verify(discoveryClient, times(1)).register();  // all but 1 is no-op
    }
}
