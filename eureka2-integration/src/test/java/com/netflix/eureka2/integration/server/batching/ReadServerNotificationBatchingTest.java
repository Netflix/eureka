package com.netflix.eureka2.integration.server.batching;

import java.util.Set;

import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.functions.InterestFunctions;
import com.netflix.eureka2.integration.EurekaDeploymentClients;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.junit.categories.ExperimentalTest;
import com.netflix.eureka2.junit.categories.IntegrationTest;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.rx.ExtTestSubscriber;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.testkit.junit.resources.EurekaDeploymentResource;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import rx.functions.Action1;

import static com.netflix.eureka2.interests.ChangeNotifications.dataOnlyFilter;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author David Liu
 */
@Category({IntegrationTest.class, ExperimentalTest.class})
public class ReadServerNotificationBatchingTest {

    private static final int REGISTRY_INITIAL_SIZE = 100;

    /**
     * We start with single write server, and scale read cluster up later, once write server has data in
     * the registry.
     */
    @Rule
    public final EurekaDeploymentResource eurekaDeploymentResource = new EurekaDeploymentResource(1, 0);

    private EurekaDeploymentClients eurekaDeploymentClients;

    @Before
    public void setUp() throws Exception {
        eurekaDeploymentClients = new EurekaDeploymentClients(eurekaDeploymentResource.getEurekaDeployment());
    }

    /**
     * Subscribe to Eureka read server, that has not uploaded yet initial content
     * from write server. Write server batching markers shell be propagated to the client
     * and a client should get all data followed by single buffer sentinel.
     */
    @Test(timeout = 60000)
    public void testColdReadCacheDataBatching() throws Exception {
        eurekaDeploymentClients.fillUpRegistry(REGISTRY_INITIAL_SIZE, SampleInstanceInfo.WebServer.build());

        // Bootstrap read server and connect Eureka client immediately
        eurekaDeploymentResource.getEurekaDeployment().getReadCluster().scaleUpByOne();
        EurekaInterestClient eurekaClient = eurekaDeploymentResource.interestClientToReadCluster();

        ExtTestSubscriber<Set<InstanceInfo>> testSubscriber = new ExtTestSubscriber<>();
        eurekaClient.forInterest(Interests.forFullRegistry())
                .doOnNext(DELAY_ACTION)
                .compose(InterestFunctions.buffers())
                .compose(InterestFunctions.snapshots())
                .subscribe(testSubscriber);

        // We should always get in the first batch all entries
        Set<InstanceInfo> initialSet = testSubscriber.takeNextOrWait();
        assertThat(initialSet.size(), is(greaterThan(REGISTRY_INITIAL_SIZE)));
    }

    /**
     * Subscribe to Eureka read server, that has all data in its own registry.
     * Read server registry batching markers shell be propagated to the client
     * and a client should get all data followed by single buffer sentinel.
     */
    @Test(timeout = 60000)
    public void testHotCacheDataBatching() throws Exception {
        // Bootstrap read server and connect Eureka client immediately
        eurekaDeploymentResource.getEurekaDeployment().getReadCluster().scaleUpByOne();
        EurekaInterestClient eurekaClient = eurekaDeploymentResource.interestClientToReadCluster();

        // Fill in the registry
        eurekaDeploymentClients.fillUpRegistry(REGISTRY_INITIAL_SIZE, SampleInstanceInfo.WebServer.build());

        // Connect with a client and take all entries, to be sure that read server registry is hot
        ExtTestSubscriber<ChangeNotification<InstanceInfo>> testSubscriber = new ExtTestSubscriber<>();
        eurekaClient.forInterest(Interests.forFullRegistry()).filter(dataOnlyFilter()).subscribe(testSubscriber);
        testSubscriber.takeNextOrWait(REGISTRY_INITIAL_SIZE + 2);
        eurekaClient.shutdown();

        // Now connect again
        eurekaClient = eurekaDeploymentResource.interestClientToReadCluster();
        testSubscriber = new ExtTestSubscriber<>();
        eurekaClient.forInterest(Interests.forFullRegistry()).subscribe(testSubscriber);
        testSubscriber.takeNextOrWait(REGISTRY_INITIAL_SIZE + 2);

        ExtTestSubscriber<Set<InstanceInfo>> snapshotSubscriber = new ExtTestSubscriber<>();
        eurekaClient.forInterest(Interests.forFullRegistry())
                .doOnNext(DELAY_ACTION)
                .compose(InterestFunctions.buffers())
                .compose(InterestFunctions.snapshots())
                .subscribe(snapshotSubscriber);

        // We should always get in the first batch all entries
        Set<InstanceInfo> initialSet = snapshotSubscriber.takeNextOrWait();
        assertThat(initialSet.size(), is(equalTo(REGISTRY_INITIAL_SIZE + 2)));
    }

    private static final Action1<ChangeNotification<InstanceInfo>> DELAY_ACTION = new Action1<ChangeNotification<InstanceInfo>>() {
        @Override
        public void call(ChangeNotification<InstanceInfo> notification) {
            // Inject processing delay, to help expose potential batch marker races.
            try {
                Thread.sleep(1);
            } catch (InterruptedException ignore) {
            }
        }
    };
}
