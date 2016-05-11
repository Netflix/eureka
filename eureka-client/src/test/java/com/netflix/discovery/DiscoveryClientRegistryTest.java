package com.netflix.discovery;

import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.netflix.appinfo.DataCenterInfo;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.discovery.junit.resource.DiscoveryClientResource;
import com.netflix.discovery.shared.Applications;
import com.netflix.discovery.shared.transport.EurekaHttpClient;
import com.netflix.discovery.shared.transport.EurekaHttpResponse;
import com.netflix.discovery.shared.transport.SimpleEurekaHttpServer;
import com.netflix.discovery.util.InstanceInfoGenerator;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import static com.netflix.discovery.shared.transport.EurekaHttpResponse.anEurekaHttpResponse;
import static com.netflix.discovery.util.EurekaEntityFunctions.copyApplications;
import static com.netflix.discovery.util.EurekaEntityFunctions.countInstances;
import static com.netflix.discovery.util.EurekaEntityFunctions.mergeApplications;
import static com.netflix.discovery.util.EurekaEntityFunctions.takeFirst;
import static com.netflix.discovery.util.EurekaEntityFunctions.toApplications;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Nitesh Kant
 */
public class DiscoveryClientRegistryTest {

    private static final String TEST_LOCAL_REGION = "us-east-1";
    private static final String TEST_REMOTE_REGION = "us-west-2";
    private static final String TEST_REMOTE_ZONE = "us-west-2c";

    private static final EurekaHttpClient requestHandler = mock(EurekaHttpClient.class);
    private static SimpleEurekaHttpServer eurekaHttpServer;

    @Rule
    public DiscoveryClientResource discoveryClientResource = DiscoveryClientResource.newBuilder()
            .withRegistration(false)
            .withRegistryFetch(true)
            .withRemoteRegions(TEST_REMOTE_REGION)
            .connectWith(eurekaHttpServer)
            .build();

    /**
     * Share server stub by all tests.
     */
    @BeforeClass
    public static void setUpClass() throws IOException {
        eurekaHttpServer = new SimpleEurekaHttpServer(requestHandler);
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        if (eurekaHttpServer != null) {
            eurekaHttpServer.shutdown();
        }
    }

    @Before
    public void setUp() throws Exception {
        reset(requestHandler);
        when(requestHandler.cancel(anyString(), anyString())).thenReturn(EurekaHttpResponse.status(200));
        when(requestHandler.getDelta()).thenReturn(
                anEurekaHttpResponse(200, new Applications()).type(MediaType.APPLICATION_JSON_TYPE).build()
        );
    }

    @Test
    public void testGetByVipInLocalRegion() throws Exception {
        Applications applications = InstanceInfoGenerator.newBuilder(4, "app1", "app2").build().toApplications();
        InstanceInfo instance = applications.getRegisteredApplications("app1").getInstances().get(0);

        when(requestHandler.getApplications(TEST_REMOTE_REGION)).thenReturn(
                anEurekaHttpResponse(200, applications).type(MediaType.APPLICATION_JSON_TYPE).build()
        );

        List<InstanceInfo> result = discoveryClientResource.getClient().getInstancesByVipAddress(instance.getVIPAddress(), false);
        assertThat(result.size(), is(equalTo(2)));
        assertThat(result.get(0).getVIPAddress(), is(equalTo(instance.getVIPAddress())));
    }

    @Test
    public void testGetAllKnownRegions() throws Exception {
        prepareRemoteRegionRegistry();

        EurekaClient client = discoveryClientResource.getClient();

        Set<String> allKnownRegions = client.getAllKnownRegions();
        assertThat(allKnownRegions.size(), is(equalTo(2)));
        assertThat(allKnownRegions, hasItem(TEST_REMOTE_REGION));
    }

    @Test
    public void testAllAppsForRegions() throws Exception {
        prepareRemoteRegionRegistry();
        EurekaClient client = discoveryClientResource.getClient();

        Applications appsForRemoteRegion = client.getApplicationsForARegion(TEST_REMOTE_REGION);
        assertThat(countInstances(appsForRemoteRegion), is(equalTo(4)));

        Applications appsForLocalRegion = client.getApplicationsForARegion(TEST_LOCAL_REGION);
        assertThat(countInstances(appsForLocalRegion), is(equalTo(4)));
    }

    @Test
    public void testCacheRefreshSingleAppForLocalRegion() throws Exception {
        InstanceInfoGenerator instanceGen = InstanceInfoGenerator.newBuilder(2, "testApp").build();
        Applications initialApps = instanceGen.takeDelta(1);
        String vipAddress = initialApps.getRegisteredApplications().get(0).getInstances().get(0).getVIPAddress();

        DiscoveryClientResource vipClientResource = discoveryClientResource.fork().withVipFetch(vipAddress).build();

        // Take first portion
        when(requestHandler.getVip(vipAddress, TEST_REMOTE_REGION)).thenReturn(
                anEurekaHttpResponse(200, initialApps).type(MediaType.APPLICATION_JSON_TYPE).build()
        );
        EurekaClient vipClient = vipClientResource.getClient();
        assertThat(countInstances(vipClient.getApplications()), is(equalTo(1)));

        // Now second one
        when(requestHandler.getVip(vipAddress, TEST_REMOTE_REGION)).thenReturn(
                anEurekaHttpResponse(200, instanceGen.toApplications()).type(MediaType.APPLICATION_JSON_TYPE).build()
        );
        assertThat(vipClientResource.awaitCacheUpdate(5, TimeUnit.SECONDS), is(true));
        assertThat(countInstances(vipClient.getApplications()), is(equalTo(2)));
    }

    @Test
    public void testEurekaClientPeriodicHeartbeat() throws Exception {
        DiscoveryClientResource registeringClientResource = discoveryClientResource.fork().withRegistration(true).withRegistryFetch(false).build();
        InstanceInfo instance = registeringClientResource.getMyInstanceInfo();
        when(requestHandler.register(any(InstanceInfo.class))).thenReturn(EurekaHttpResponse.status(204));
        when(requestHandler.sendHeartBeat(instance.getAppName(), instance.getId(), null, null)).thenReturn(anEurekaHttpResponse(200, InstanceInfo.class).build());

        registeringClientResource.getClient(); // Initialize
        verify(requestHandler, timeout(5 * 1000).atLeast(2)).sendHeartBeat(instance.getAppName(), instance.getId(), null, null);
    }

    @Test
    public void testEurekaClientPeriodicCacheRefresh() throws Exception {
        InstanceInfoGenerator instanceGen = InstanceInfoGenerator.newBuilder(3, 1).build();
        Applications initialApps = instanceGen.takeDelta(1);

        // Full fetch
        when(requestHandler.getApplications(TEST_REMOTE_REGION)).thenReturn(
                anEurekaHttpResponse(200, initialApps).type(MediaType.APPLICATION_JSON_TYPE).build()
        );

        EurekaClient client = discoveryClientResource.getClient();
        assertThat(countInstances(client.getApplications()), is(equalTo(1)));

        // Delta 1
        when(requestHandler.getDelta(TEST_REMOTE_REGION)).thenReturn(
                anEurekaHttpResponse(200, instanceGen.takeDelta(1)).type(MediaType.APPLICATION_JSON_TYPE).build()
        );
        assertThat(discoveryClientResource.awaitCacheUpdate(5, TimeUnit.SECONDS), is(true));

        // Delta 2
        when(requestHandler.getDelta(TEST_REMOTE_REGION)).thenReturn(
                anEurekaHttpResponse(200, instanceGen.takeDelta(1)).type(MediaType.APPLICATION_JSON_TYPE).build()
        );
        assertThat(discoveryClientResource.awaitCacheUpdate(5, TimeUnit.SECONDS), is(true));

        assertThat(countInstances(client.getApplications()), is(equalTo(3)));
    }

    @Test
    public void testGetInvalidVIP() throws Exception {
        Applications applications = InstanceInfoGenerator.newBuilder(1, "testApp").build().toApplications();
        when(requestHandler.getApplications(TEST_REMOTE_REGION)).thenReturn(
                anEurekaHttpResponse(200, applications).type(MediaType.APPLICATION_JSON_TYPE).build()
        );

        EurekaClient client = discoveryClientResource.getClient();
        assertThat(countInstances(client.getApplications()), is(equalTo(1)));

        List<InstanceInfo> instancesByVipAddress = client.getInstancesByVipAddress("XYZ", false);
        assertThat(instancesByVipAddress.isEmpty(), is(true));
    }

    @Test
    public void testGetInvalidVIPForRemoteRegion() throws Exception {
        prepareRemoteRegionRegistry();

        EurekaClient client = discoveryClientResource.getClient();
        List<InstanceInfo> instancesByVipAddress = client.getInstancesByVipAddress("XYZ", false, TEST_REMOTE_REGION);
        assertThat(instancesByVipAddress.isEmpty(), is(true));
    }

    @Test
    public void testGetByVipInRemoteRegion() throws Exception {
        prepareRemoteRegionRegistry();

        EurekaClient client = discoveryClientResource.getClient();
        String vipAddress = takeFirst(client.getApplicationsForARegion(TEST_REMOTE_REGION)).getVIPAddress();

        List<InstanceInfo> instancesByVipAddress = client.getInstancesByVipAddress(vipAddress, false, TEST_REMOTE_REGION);
        assertThat(instancesByVipAddress.size(), is(equalTo(2)));
        InstanceInfo instance = instancesByVipAddress.iterator().next();
        assertThat(instance.getVIPAddress(), is(equalTo(vipAddress)));
    }

    @Test
    public void testAppsHashCodeAfterRefresh() throws Exception {
        InstanceInfoGenerator instanceGen = InstanceInfoGenerator.newBuilder(2, "testApp").build();

        // Full fetch with one item
        InstanceInfo first = instanceGen.first();
        Applications initial = toApplications(first);
        when(requestHandler.getApplications(TEST_REMOTE_REGION)).thenReturn(
                anEurekaHttpResponse(200, initial).type(MediaType.APPLICATION_JSON_TYPE).build()
        );
        EurekaClient client = discoveryClientResource.getClient();
        assertThat(client.getApplications().getAppsHashCode(), is(equalTo("UP_1_")));

        // Delta with one add
        InstanceInfo second = new InstanceInfo.Builder(instanceGen.take(1)).setStatus(InstanceStatus.DOWN).build();
        Applications delta = toApplications(second);
        delta.setAppsHashCode("DOWN_1_UP_1_");

        when(requestHandler.getDelta(TEST_REMOTE_REGION)).thenReturn(
                anEurekaHttpResponse(200, delta).type(MediaType.APPLICATION_JSON_TYPE).build()
        );

        assertThat(discoveryClientResource.awaitCacheUpdate(5, TimeUnit.SECONDS), is(true));
        assertThat(client.getApplications().getAppsHashCode(), is(equalTo("DOWN_1_UP_1_")));
    }


    @Test
    public void testApplyDeltaWithBadInstanceInfoDataCenterInfoAsNull() throws Exception {
        InstanceInfoGenerator instanceGen = InstanceInfoGenerator.newBuilder(2, "testApp").build();

        // Full fetch with one item
        InstanceInfo first = instanceGen.first();
        Applications initial = toApplications(first);
        when(requestHandler.getApplications(TEST_REMOTE_REGION)).thenReturn(
                anEurekaHttpResponse(200, initial).type(MediaType.APPLICATION_JSON_TYPE).build()
        );
        EurekaClient client = discoveryClientResource.getClient();
        assertThat(client.getApplications().getAppsHashCode(), is(equalTo("UP_1_")));

        // Delta with one add
        InstanceInfo second = new InstanceInfo.Builder(instanceGen.take(1)).setInstanceId("foo1").setStatus(InstanceStatus.DOWN).setDataCenterInfo(null).build();
        InstanceInfo third = new InstanceInfo.Builder(instanceGen.take(1)).setInstanceId("foo2").setStatus(InstanceStatus.UP).setDataCenterInfo(new DataCenterInfo() {
            @Override
            public Name getName() {
                return null;
            }
        }).build();
        Applications delta = toApplications(second, third);
        delta.setAppsHashCode("DOWN_1_UP_2_");

        when(requestHandler.getDelta(TEST_REMOTE_REGION)).thenReturn(
                anEurekaHttpResponse(200, delta).type(MediaType.APPLICATION_JSON_TYPE).build()
        );

        assertThat(discoveryClientResource.awaitCacheUpdate(5, TimeUnit.SECONDS), is(true));
        assertThat(client.getApplications().getAppsHashCode(), is(equalTo("DOWN_1_UP_2_")));
    }

    /**
     * There is a bug, because of which remote registry data structures are not initialized during full registry fetch, only during delta.
     */
    private void prepareRemoteRegionRegistry() throws Exception {
        Applications localApplications = InstanceInfoGenerator.newBuilder(4, "app1", "app2").build().toApplications();
        Applications remoteApplications = InstanceInfoGenerator.newBuilder(4, "remote1", "remote2").withZone(TEST_REMOTE_ZONE).build().toApplications();

        Applications allApplications = mergeApplications(localApplications, remoteApplications);

        // Load remote data in delta, to go around exiting bug in DiscoveryClient
        Applications delta = copyApplications(remoteApplications);
        delta.setAppsHashCode(allApplications.getAppsHashCode());

        when(requestHandler.getApplications(TEST_REMOTE_REGION)).thenReturn(
                anEurekaHttpResponse(200, localApplications).type(MediaType.APPLICATION_JSON_TYPE).build()
        );
        when(requestHandler.getDelta(TEST_REMOTE_REGION)).thenReturn(
                anEurekaHttpResponse(200, delta).type(MediaType.APPLICATION_JSON_TYPE).build()
        );

        assertThat(discoveryClientResource.awaitCacheUpdate(5, TimeUnit.SECONDS), is(true));
    }
}
