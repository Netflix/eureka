package com.netflix.eureka2.eureka1.rest.query;

import java.util.concurrent.TimeUnit;

import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.Interest.Operator;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.registry.SourcedRegistryMockResource;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import org.junit.Rule;
import org.junit.Test;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Tomasz Bak
 */
public class Eureka2RegistryViewCacheTest {

    private static final int APPLICATION_CLUSTER_SIZE = 3;
    private static final long REFRESH_INTERVAL = 30000;
    private static final long QUERY_TIMEOUT = 30000;

    public static final String VIP_SERVER_FARM = "vip#serverFarm";
    public static final Interest<InstanceInfo> VIP_INTEREST = Interests.forVips(Operator.Equals, VIP_SERVER_FARM);

    private final TestScheduler testScheduler = Schedulers.test();

    public static final String SECURE_VIP_SERVER_FARM = "secureVip#serverFarm";
    public static final Interest<InstanceInfo> SECURE_VIP_INTEREST = Interests.forSecureVips(Operator.Equals, SECURE_VIP_SERVER_FARM);

    @Rule
    public final SourcedRegistryMockResource registryMockResource = new SourcedRegistryMockResource();

    private final Eureka2RegistryViewCache cache = new Eureka2RegistryViewCache(registryMockResource.registry(), REFRESH_INTERVAL, QUERY_TIMEOUT, testScheduler);

    @Test
    public void testAllApplicationsCaching() throws Exception {
        registryMockResource.batchStart();
        registryMockResource.uploadClusterToRegistry(SampleInstanceInfo.WebServer, APPLICATION_CLUSTER_SIZE);
        registryMockResource.uploadClusterToRegistry(SampleInstanceInfo.Backend, APPLICATION_CLUSTER_SIZE);
        registryMockResource.batchEnd();

        Applications applications = cache.findAllApplications();
        assertThat(applications.getRegisteredApplications().size(), is(equalTo(2)));

        // Check the same copy is returned on subsequent call
        assertThat(cache.findAllApplications() == applications, is(true));
    }

    @Test
    public void testApplicationsDeltaCaching() throws Exception {
        // Generate first cache copy
        registryMockResource.batchStart();
        registryMockResource.uploadClusterToRegistry(SampleInstanceInfo.WebServer, APPLICATION_CLUSTER_SIZE);
        registryMockResource.batchEnd();

        Applications firstAppDelta = cache.findAllApplicationsDelta();
        assertThat(firstAppDelta.getRegisteredApplications().size(), is(equalTo(0)));

        // Generate second copy
        registryMockResource.batchStart();
        String backend = registryMockResource.uploadClusterToRegistry(SampleInstanceInfo.Backend, APPLICATION_CLUSTER_SIZE);
        registryMockResource.batchEnd();
        testScheduler.advanceTimeBy(REFRESH_INTERVAL, TimeUnit.MILLISECONDS);

        Applications secondAppDelta = cache.findAllApplicationsDelta();
        assertThat(secondAppDelta.getRegisteredApplications().size(), is(equalTo(1)));
        Application deltaApp = secondAppDelta.getRegisteredApplications().get(0);
        assertThat(deltaApp.getName(), is(equalToIgnoringCase(backend)));
    }

    @Test
    public void testApplicationCaching() throws Exception {
        String appName = registryMockResource.uploadClusterBatchToRegistry(SampleInstanceInfo.WebServer, APPLICATION_CLUSTER_SIZE);

        Application application = cache.findApplication(appName);
        assertThat(application.getName(), is(equalToIgnoringCase(appName)));

        // Check the same copy is returned on subsequent call
        assertThat(cache.findApplication(appName) == application, is(true));
    }

    @Test
    public void testVipCaching() throws Exception {
        registryMockResource.batchStart(VIP_INTEREST);
        InstanceInfo webServerTemplate = SampleInstanceInfo.WebServer.builder().withVipAddress(VIP_SERVER_FARM).build();
        registryMockResource.uploadClusterToRegistry(webServerTemplate, APPLICATION_CLUSTER_SIZE);
        InstanceInfo backendTemplate = SampleInstanceInfo.Backend.builder().withVipAddress(VIP_SERVER_FARM).build();
        registryMockResource.uploadClusterToRegistry(backendTemplate, APPLICATION_CLUSTER_SIZE);
        registryMockResource.batchEnd(VIP_INTEREST);

        Applications applications = cache.findApplicationsByVip(VIP_SERVER_FARM);
        assertThat(applications.getRegisteredApplications().size(), is(equalTo(2)));

        // Check the same copy is returned on subsequent call
        assertThat(cache.findApplicationsByVip(VIP_SERVER_FARM) == applications, is(true));
    }

    @Test
    public void testSecureVipCaching() throws Exception {
        registryMockResource.batchStart(SECURE_VIP_INTEREST);
        InstanceInfo webServerTemplate = SampleInstanceInfo.WebServer.builder().withSecureVipAddress(SECURE_VIP_SERVER_FARM).build();
        registryMockResource.uploadClusterToRegistry(webServerTemplate, APPLICATION_CLUSTER_SIZE);
        InstanceInfo backendTemplate = SampleInstanceInfo.Backend.builder().withSecureVipAddress(SECURE_VIP_SERVER_FARM).build();
        registryMockResource.uploadClusterToRegistry(backendTemplate, APPLICATION_CLUSTER_SIZE);
        registryMockResource.batchEnd(SECURE_VIP_INTEREST);

        Applications applications = cache.findApplicationsBySecureVip(SECURE_VIP_SERVER_FARM);
        assertThat(applications.getRegisteredApplications().size(), is(equalTo(2)));

        // Check the same copy is returned on subsequent call
        assertThat(cache.findApplicationsBySecureVip(SECURE_VIP_SERVER_FARM) == applications, is(true));
    }

    @Test
    public void testInstanceCaching() throws Exception {
        InstanceInfo instanceInfo = SampleInstanceInfo.WebServer.build();
        Interest<InstanceInfo> interest = Interests.forInstance(Operator.Equals, instanceInfo.getId());
        registryMockResource.batchStart(interest);
        registryMockResource.uploadToRegistry(instanceInfo);
        registryMockResource.batchEnd(interest);

        com.netflix.appinfo.InstanceInfo v1InstanceInfo = cache.findInstance(instanceInfo.getId());
        assertThat(v1InstanceInfo.getAppName(), is(equalToIgnoringCase(instanceInfo.getApp())));

        // Check the same copy is returned on subsequent call
        assertThat(cache.findInstance(instanceInfo.getId()) == v1InstanceInfo, is(true));
    }
}