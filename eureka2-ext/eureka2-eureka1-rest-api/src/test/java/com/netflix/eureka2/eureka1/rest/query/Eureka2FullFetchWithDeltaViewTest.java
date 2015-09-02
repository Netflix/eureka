package com.netflix.eureka2.eureka1.rest.query;

import java.util.concurrent.TimeUnit;

import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.eureka2.eureka1.rest.query.Eureka2FullFetchWithDeltaView.RegistryFetch;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.registry.EurekaRegistry;
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
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertSame;
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

    private final EurekaRegistry<InstanceInfo> registry = registryMockResource.registry();

    private Eureka2FullFetchWithDeltaView view;

    @Before
    public void setUp() throws Exception {
        view = new Eureka2FullFetchWithDeltaView(registry.forInterest(Interests.forFullRegistry()), REFRESH_INTERVAL, testScheduler);
    }

    @Test
    public void testAfterFirstBatchApplicationsAndDeltaAreAvailable() throws Exception {
        String webAppName = registryMockResource.uploadClusterBatchToRegistry(SampleInstanceInfo.WebServer, APPLICATION_CLUSTER_SIZE);
        RegistryFetch registryFetch = latestCopy();

        Applications fullFetch = registryFetch.getApplications();
        Applications delta = registryFetch.getDeltaChanges();

        verifyApplicationsPresent(fullFetch, webAppName);
        assertThat(delta.getRegisteredApplications().size(), is(equalTo(0)));

        verifyHashCodes(fullFetch, delta);
    }

    @Test
    public void testSecondBatchIncludedInApplicationsAndDelta() throws Exception {
        // First batch
        String webAppName = registryMockResource.uploadClusterBatchToRegistry(SampleInstanceInfo.WebServer, APPLICATION_CLUSTER_SIZE);
        testScheduler.triggerActions();
        RegistryFetch firstCopy = latestCopy();

        // Second batch
        String backendName = registryMockResource.uploadClusterBatchToRegistry(SampleInstanceInfo.Backend, APPLICATION_CLUSTER_SIZE);
        assertSame(firstCopy, latestCopy());
        testScheduler.advanceTimeBy(REFRESH_INTERVAL, TimeUnit.MILLISECONDS);

        RegistryFetch secondCopy = latestCopy();

        Applications fullFetch = secondCopy.getApplications();
        Applications delta = secondCopy.getDeltaChanges();

        verifyApplicationsPresent(fullFetch, webAppName, backendName);
        verifyApplicationsPresent(delta, backendName);

        verifyHashCodes(fullFetch, delta);
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

        Applications fullFetch = registryFetch.getApplications();
        Applications delta = registryFetch.getDeltaChanges();

        verifyApplicationsPresent(fullFetch, backendName);
        verifyApplicationsNotPresent(fullFetch, webAppName);
        verifyApplicationsPresent(delta, backendName);

        verifyHashCodes(fullFetch, delta);
    }

    @Test
    public void testModifiedElementsUpdatedInApplicationsAndPresentInDelta() throws Exception {
        InstanceInfo firstInstance = SampleInstanceInfo.WebServer.build();
        Interest<InstanceInfo> appInterest = Interests.forApplications(firstInstance.getApp());

        // First batch
        registryMockResource.uploadBatchToRegistry(appInterest, firstInstance);
        latestCopy();

        // Second batch
        InstanceInfo secondInstance = new InstanceInfo.Builder().withInstanceInfo(firstInstance).withAppGroup("new_app_group").build();
        registryMockResource.uploadBatchToRegistry(appInterest, secondInstance);

        testScheduler.advanceTimeBy(REFRESH_INTERVAL, TimeUnit.MILLISECONDS);

        RegistryFetch registryFetch = latestCopy();

        Applications fullFetch = registryFetch.getApplications();
        Applications delta = registryFetch.getDeltaChanges();

        assertThat(fullFetch.getRegisteredApplications().size(), is(equalTo(1)));
        Application application = fullFetch.getRegisteredApplications().get(0);
        assertThat(application.getName(), is(equalToIgnoringCase(secondInstance.getApp())));

        assertThat(application.getInstances().size(), is(equalTo(1)));
        com.netflix.appinfo.InstanceInfo instanceInfo = application.getInstances().get(0);
        assertThat(instanceInfo.getAppGroupName(), is(equalToIgnoringCase("new_app_group")));

        verifyHashCodes(fullFetch, delta);
    }

    private RegistryFetch latestCopy() {
        testScheduler.triggerActions();
        return view.latestCopy().timeout(1, TimeUnit.SECONDS).take(1).toBlocking().first();
    }

    private static void verifyHashCodes(Applications fullFetch, Applications delta) {
        assertThat(fullFetch.getAppsHashCode(), is(equalTo(delta.getAppsHashCode())));
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