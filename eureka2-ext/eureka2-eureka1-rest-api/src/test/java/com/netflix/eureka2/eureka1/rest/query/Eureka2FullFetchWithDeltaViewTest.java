package com.netflix.eureka2.eureka1.rest.query;

import java.util.concurrent.TimeUnit;

import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.eureka2.eureka1.rest.query.Eureka2FullFetchWithDeltaView.RegistryFetch;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.SourcedRegistryMockResource;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import rx.functions.Action1;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

/**
 * @author Tomasz Bak
 */
public class Eureka2FullFetchWithDeltaViewTest {

    private static final int APPLICATION_CLUSTER_SIZE = 3;
    private static final long REFRESH_INTERVAL = 30000;

    private final TestScheduler testScheduler = Schedulers.test();

    @Rule
    public final SourcedRegistryMockResource registryMockResource = new SourcedRegistryMockResource();

    private final SourcedEurekaRegistry<InstanceInfo> registry = registryMockResource.registry();

    private Eureka2FullFetchWithDeltaView view;

    @Before
    public void setUp() throws Exception {
        view = new Eureka2FullFetchWithDeltaView(registry.forInterest(Interests.forFullRegistry()), REFRESH_INTERVAL, testScheduler);
    }

    @Test
    public void testAfterFirstBatchApplicationsAndDeltaAreAvailable() throws Exception {
        String webAppName = registryMockResource.uploadClusterBatchToRegistry(SampleInstanceInfo.WebServer, APPLICATION_CLUSTER_SIZE);
        RegistryFetch registryFetch = latestCopy();

        verifyApplicationsPresent(registryFetch.getApplications(), webAppName);
        assertThat(registryFetch.getDeltaChanges().getRegisteredApplications().size(), is(equalTo(0)));
    }

    @Test
    public void testSecondBatchIncludedInApplicationsAndDelta() throws Exception {
        // First batch
        String webAppName = registryMockResource.uploadClusterBatchToRegistry(SampleInstanceInfo.WebServer, APPLICATION_CLUSTER_SIZE);
        testScheduler.triggerActions();

        // Second batch
        String backendName = registryMockResource.uploadClusterBatchToRegistry(SampleInstanceInfo.Backend, APPLICATION_CLUSTER_SIZE);
        testScheduler.advanceTimeBy(REFRESH_INTERVAL, TimeUnit.MILLISECONDS);

        RegistryFetch registryFetch = latestCopy();

        verifyApplicationsPresent(registryFetch.getApplications(), webAppName, backendName);
        verifyApplicationsPresent(registryFetch.getDeltaChanges(), backendName);
    }

    @Test
    public void testRemovedElementsDroppedFromApplicationsAndPresentInDelta() throws Exception {
        // First batch
        String webAppName = registryMockResource.uploadClusterBatchToRegistry(SampleInstanceInfo.WebServer, APPLICATION_CLUSTER_SIZE);
        testScheduler.triggerActions();

        // Second batch
        String backendName = registryMockResource.uploadClusterBatchToRegistry(SampleInstanceInfo.Backend, APPLICATION_CLUSTER_SIZE);
        registry.forInterest(Interests.forApplications(webAppName)).subscribe(new Action1<ChangeNotification<InstanceInfo>>() {
            @Override
            public void call(ChangeNotification<InstanceInfo> notification) {
                if (notification.getKind() == Kind.Add) {
                    registryMockResource.removeFromRegistry(notification.getData());
                }
            }
        });
        testScheduler.advanceTimeBy(REFRESH_INTERVAL, TimeUnit.MILLISECONDS);

        RegistryFetch registryFetch = latestCopy();

        verifyApplicationsPresent(registryFetch.getApplications(), backendName);
        verifyApplicationsNotPresent(registryFetch.getApplications(), webAppName);
        verifyApplicationsPresent(registryFetch.getDeltaChanges(), backendName);
    }

    private RegistryFetch latestCopy() {
        testScheduler.triggerActions();
        return view.latestCopy().timeout(1, TimeUnit.SECONDS).take(1).toBlocking().first();
    }

    private static void verifyApplicationsNotPresent(Applications applications, String... appNames) {
        for (String appName : appNames) {
            assertThat(applications.getRegisteredApplications(appName), is(nullValue()));
        }
    }

    private static void verifyApplicationsPresent(Applications applications, String... appNames) {
        for (String appName : appNames) {
            Application app = applications.getRegisteredApplications(appName);
            assertThat(app.getInstances().size(), is(equalTo(APPLICATION_CLUSTER_SIZE)));
        }
    }
}