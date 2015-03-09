package com.netflix.eureka2.eureka1.rest.query;

import java.util.concurrent.TimeUnit;

import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.SourcedRegistryMockResource;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Tomasz Bak
 */
public class AbstractEureka2RegistryViewTest {

    private static final int APPLICATION_CLUSTER_SIZE = 3;
    private static final long REFRESH_INTERVAL = 30000;

    private final TestScheduler testScheduler = Schedulers.test();

    @Rule
    public final SourcedRegistryMockResource registryMockResource = new SourcedRegistryMockResource();

    private final SourcedEurekaRegistry<InstanceInfo> registry = registryMockResource.registry();
    private Eureka2ApplicationsView view;

    @Before
    public void setUp() throws Exception {
        view = new Eureka2ApplicationsView(registry.forInterest(Interests.forFullRegistry()), REFRESH_INTERVAL, testScheduler);
    }

    @Test
    public void testAfterFirstBatchLatestCopyIsAvailable() throws Exception {
        String webAppName = registryMockResource.uploadClusterBatchToRegistry(SampleInstanceInfo.WebServer, APPLICATION_CLUSTER_SIZE);
        testScheduler.triggerActions();

        verifyApplicationsInSnapshot(webAppName);
    }

    @Test
    public void testAfterRefreshIntervalLatestCopyIsUpdated() throws Exception {
        // First batch
        String webAppName = registryMockResource.uploadClusterBatchToRegistry(SampleInstanceInfo.WebServer, APPLICATION_CLUSTER_SIZE);
        testScheduler.triggerActions();

        verifyApplicationsInSnapshot(webAppName);

        // Now add second cluster
        String backendName = registryMockResource.uploadClusterBatchToRegistry(SampleInstanceInfo.Backend, APPLICATION_CLUSTER_SIZE);
        testScheduler.advanceTimeBy(REFRESH_INTERVAL, TimeUnit.MILLISECONDS);

        verifyApplicationsInSnapshot(webAppName, backendName);
    }

    private void verifyApplicationsInSnapshot(String... appNames) {
        Applications applications = view.latestCopy().timeout(1, TimeUnit.SECONDS).take(1).toBlocking().first();

        for (String appName : appNames) {
            Application webApp = applications.getRegisteredApplications(appName);
            assertThat(webApp.getInstances().size(), is(equalTo(APPLICATION_CLUSTER_SIZE)));
        }
    }
}