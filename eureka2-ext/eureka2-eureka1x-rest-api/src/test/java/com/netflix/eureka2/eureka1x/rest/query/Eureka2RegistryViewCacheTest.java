package com.netflix.eureka2.eureka1x.rest.query;

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

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Tomasz Bak
 */
public class Eureka2RegistryViewCacheTest {

    private static final int APPLICATION_CLUSTER_SIZE = 3;

    public static final String VIP_SERVER_FARM = "vip#serverFarm";
    public static final Interest<InstanceInfo> VIP_INTEREST = Interests.forVips(Operator.Equals, VIP_SERVER_FARM);

    public static final String SECURE_VIP_SERVER_FARM = "secureVip#serverFarm";
    public static final Interest<InstanceInfo> SECURE_VIP_INTEREST = Interests.forSecureVips(Operator.Equals, SECURE_VIP_SERVER_FARM);

    @Rule
    public final SourcedRegistryMockResource registryMockResource = new SourcedRegistryMockResource();

    private final Eureka2RegistryViewCache cache = new Eureka2RegistryViewCache(registryMockResource.registry());

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