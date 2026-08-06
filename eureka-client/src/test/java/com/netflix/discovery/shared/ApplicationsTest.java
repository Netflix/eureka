package com.netflix.discovery.shared;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import com.google.common.collect.Iterables;
import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.DataCenterInfo;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.AmazonInfo.MetaDataKey;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.discovery.AzToRegionMapper;
import com.netflix.discovery.DefaultEurekaClientConfig;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.discovery.InstanceRegionChecker;
import com.netflix.discovery.InstanceRegionCheckerTest;
import com.netflix.discovery.PropertyBasedAzToRegionMapper;

public class ApplicationsTest {

    @Test
    public void testVersionAndAppHash() {
        Applications apps = new Applications();
        assertEquals(-1L, (long)apps.getVersion());
        assertNull(apps.getAppsHashCode());
        
        apps.setVersion(101L);
        apps.setAppsHashCode("UP_5_DOWN_6_");
        assertEquals(101L, (long)apps.getVersion());
        assertEquals("UP_5_DOWN_6_", apps.getAppsHashCode());
    }
    
    /**
     * Test that instancesMap in Application and shuffleVirtualHostNameMap in
     * Applications are correctly updated when the last instance is removed from
     * an application and shuffleInstances has been run.
     */
    @Test
    public void shuffleVirtualHostNameMapLastInstanceTest() {
        DataCenterInfo myDCI = new DataCenterInfo() {
            public DataCenterInfo.Name getName() {
                return DataCenterInfo.Name.MyOwn;
            }
        };
        InstanceInfo instanceInfo = InstanceInfo.Builder.newBuilder().setAppName("test")
                .setVIPAddress("test.testname:1").setDataCenterInfo(myDCI).setHostName("test.hostname").build();

        Application application = new Application("TestApp");
        application.addInstance(instanceInfo);
        Applications applications = new Applications();
        applications.addApplication(application);
        applications.shuffleInstances(true);

        List<InstanceInfo> testApp = applications.getInstancesByVirtualHostName("test.testname:1");
        assertEquals(Iterables.getOnlyElement(testApp), application.getByInstanceId("test.hostname"));

        application.removeInstance(instanceInfo);
        assertEquals(0, applications.size());

        applications.shuffleInstances(true);
        testApp = applications.getInstancesByVirtualHostName("test.testname:1");
        assertTrue(testApp.isEmpty());

        assertNull(application.getByInstanceId("test.hostname"));
    }

    /**
     * Test that instancesMap in Application and shuffleVirtualHostNameMap in
     * Applications are correctly updated when the last instance is removed from
     * an application and shuffleInstances has been run.
     */
    @Test
    public void shuffleSecureVirtualHostNameMapLastInstanceTest() {
        DataCenterInfo myDCI = new DataCenterInfo() {
            public DataCenterInfo.Name getName() {
                return DataCenterInfo.Name.MyOwn;
            }
        };
        InstanceInfo instanceInfo = InstanceInfo.Builder.newBuilder().setAppName("test")
                .setVIPAddress("test.testname:1").setSecureVIPAddress("securetest.testname:7102")
                .setDataCenterInfo(myDCI).setHostName("test.hostname").build();

        Application application = new Application("TestApp");
        application.addInstance(instanceInfo);

        Applications applications = new Applications();
        assertEquals(0, applications.size());

        applications.addApplication(application);
        assertEquals(1, applications.size());

        applications.shuffleInstances(true);
        List<InstanceInfo> testApp = applications.getInstancesByVirtualHostName("test.testname:1");

        assertEquals(Iterables.getOnlyElement(testApp), application.getByInstanceId("test.hostname"));

        application.removeInstance(instanceInfo);
        assertNull(application.getByInstanceId("test.hostname"));
        assertEquals(0, applications.size());

        applications.shuffleInstances(true);
        testApp = applications.getInstancesBySecureVirtualHostName("securetest.testname:7102");
        assertTrue(testApp.isEmpty());

        assertNull(application.getByInstanceId("test.hostname"));
    }
    
    /**
     * Test that instancesMap in Application and shuffleVirtualHostNameMap in
     * Applications are correctly updated when the last instance is removed from
     * an application and shuffleInstances has been run.
     */
    @Test
    public void shuffleRemoteRegistryTest() throws Exception {
        AmazonInfo ai1 = AmazonInfo.Builder.newBuilder()
                .addMetadata(MetaDataKey.availabilityZone, "us-east-1a")
                .build();
        InstanceInfo instanceInfo1 = InstanceInfo.Builder.newBuilder().setAppName("test")
                .setVIPAddress("test.testname:1")
                .setSecureVIPAddress("securetest.testname:7102")
                .setDataCenterInfo(ai1)
                .setAppName("TestApp")
                .setHostName("test.east.hostname")        
                .build();
        AmazonInfo ai2 = AmazonInfo.Builder.newBuilder()
                .addMetadata(MetaDataKey.availabilityZone, "us-west-2a")
                .build();
        InstanceInfo instanceInfo2 = InstanceInfo.Builder.newBuilder().setAppName("test")
                .setVIPAddress("test.testname:1")
                .setSecureVIPAddress("securetest.testname:7102")
                .setDataCenterInfo(ai2)
                .setAppName("TestApp")
                .setHostName("test.west.hostname")        
                .build();

        Application application = new Application("TestApp");
        application.addInstance(instanceInfo1);
        application.addInstance(instanceInfo2);

        Applications applications = new Applications();
        assertEquals(0, applications.size());

        applications.addApplication(application);
        assertEquals(2, applications.size());

        EurekaClientConfig clientConfig = Mockito.mock(EurekaClientConfig.class);
        Mockito.when(clientConfig.getAvailabilityZones("us-east-1")).thenReturn(new String[] {"us-east-1a", "us-east-1b", "us-east-1c", "us-east-1d", "us-east-1e", "us-east-1f"});
        Mockito.when(clientConfig.getAvailabilityZones("us-west-2")).thenReturn(new String[] {"us-west-2a", "us-west-2b", "us-west-2c"});
        Mockito.when(clientConfig.getRegion()).thenReturn("us-east-1");
        Constructor<?> ctor = InstanceRegionChecker.class.getDeclaredConstructor(AzToRegionMapper.class, String.class);
        ctor.setAccessible(true);
        PropertyBasedAzToRegionMapper azToRegionMapper = new PropertyBasedAzToRegionMapper(clientConfig);
        azToRegionMapper.setRegionsToFetch(new String[] {"us-east-1", "us-west-2"});
        InstanceRegionChecker instanceRegionChecker = (InstanceRegionChecker)ctor.newInstance(azToRegionMapper, "us-west-2");
        Map<String, Applications> remoteRegionsRegistry = new HashMap<>();
        remoteRegionsRegistry.put("us-east-1", new Applications());
        applications.shuffleAndIndexInstances(remoteRegionsRegistry, clientConfig, instanceRegionChecker);
        assertNotNull(remoteRegionsRegistry.get("us-east-1").getRegisteredApplications("TestApp").getByInstanceId("test.east.hostname"));
        assertNull(applications.getRegisteredApplications("TestApp").getByInstanceId("test.east.hostname"));
        assertNull(remoteRegionsRegistry.get("us-east-1").getRegisteredApplications("TestApp").getByInstanceId("test.west.hostname"));
        assertNotNull(applications.getRegisteredApplications("TestApp").getByInstanceId("test.west.hostname"));
   
    }

    @Test
    public void testInfoDetailApplications(){

        DataCenterInfo myDCI = new DataCenterInfo() {
            public DataCenterInfo.Name getName() {
                return DataCenterInfo.Name.MyOwn;
            }
        };
        InstanceInfo instanceInfo = InstanceInfo.Builder.newBuilder()
                .setInstanceId("test.id")
                .setAppName("test")
                .setHostName("test.hostname")
                .setStatus(InstanceStatus.UP)
                .setIPAddr("test.testip:1")
                .setPort(8080)
                .setSecurePort(443)
                .setDataCenterInfo(myDCI)
                .build();

        Application application = new Application("Test App");
        application.addInstance(instanceInfo);

        Applications applications = new Applications();
        applications.addApplication(application);

        List<InstanceInfo> instanceInfos = application.getInstances();
        Assert.assertEquals(1, instanceInfos.size());
        Assert.assertTrue(instanceInfos.contains(instanceInfo));

        List<Application> appsList = applications.getRegisteredApplications();
        Assert.assertEquals(1, appsList.size());
        Assert.assertTrue(appsList.contains(application));
        Assert.assertEquals(application, applications.getRegisteredApplications(application.getName()));
    }

    @Test
    public void testRegisteredApplications() {
        DataCenterInfo myDCI = new DataCenterInfo() {
            public DataCenterInfo.Name getName() {
                return DataCenterInfo.Name.MyOwn;
            }
        };
        InstanceInfo instanceInfo = InstanceInfo.Builder.newBuilder()
                .setAppName("test")
                .setVIPAddress("test.testname:1")
                .setSecureVIPAddress("securetest.testname:7102")
                .setDataCenterInfo(myDCI)
                .setHostName("test.hostname")
                .build();

        Application application = new Application("TestApp");
        application.addInstance(instanceInfo);

        Applications applications = new Applications();
        applications.addApplication(application);
        
        List<Application> appsList = applications.getRegisteredApplications();
        Assert.assertEquals(1, appsList.size());
        Assert.assertTrue(appsList.contains(application));
        Assert.assertEquals(application, applications.getRegisteredApplications(application.getName()));
    }
    
    @Test
    public void testRegisteredApplicationsConstructor() {
        DataCenterInfo myDCI = new DataCenterInfo() {
            public DataCenterInfo.Name getName() {
                return DataCenterInfo.Name.MyOwn;
            }
        };
        InstanceInfo instanceInfo = InstanceInfo.Builder.newBuilder()
                .setAppName("test")
                .setVIPAddress("test.testname:1")
                .setSecureVIPAddress("securetest.testname:7102")
                .setDataCenterInfo(myDCI)
                .setHostName("test.hostname")
                .build();

        Application application = new Application("TestApp");
        application.addInstance(instanceInfo);

        Applications applications = new Applications("UP_1_", -1L, Arrays.asList(application));
        
        List<Application> appsList = applications.getRegisteredApplications();
        Assert.assertEquals(1, appsList.size());
        Assert.assertTrue(appsList.contains(application));
        Assert.assertEquals(application, applications.getRegisteredApplications(application.getName()));
    }
    
    @Test
    public void testApplicationsHashAndVersion() {
        Applications applications = new Applications("appsHashCode", 1L, Collections.emptyList());
        assertEquals(1L, (long)applications.getVersion());
        assertEquals("appsHashCode", applications.getAppsHashCode());
    }   
    
    @Test
    public void testPopulateInstanceCount() {
        DataCenterInfo myDCI = new DataCenterInfo() {
            public DataCenterInfo.Name getName() {
                return DataCenterInfo.Name.MyOwn;
            }
        };
        InstanceInfo instanceInfo = InstanceInfo.Builder.newBuilder()
                .setAppName("test")
                .setVIPAddress("test.testname:1")
                .setSecureVIPAddress("securetest.testname:7102")
                .setDataCenterInfo(myDCI)
                .setHostName("test.hostname")
                .setStatus(InstanceStatus.UP)
                .build();

        Application application = new Application("TestApp");
        application.addInstance(instanceInfo);

        Applications applications = new Applications();
        applications.addApplication(application);
        
        TreeMap<String, AtomicInteger> instanceCountMap = new TreeMap<>();
        applications.populateInstanceCountMap(instanceCountMap);
        assertEquals(1, instanceCountMap.size());
        assertNotNull(instanceCountMap.get(InstanceStatus.UP.name()));
        assertEquals(1, instanceCountMap.get(InstanceStatus.UP.name()).get());
        
    }
    
    @Test
    public void testGetNextIndex() {
        DataCenterInfo myDCI = new DataCenterInfo() {
            public DataCenterInfo.Name getName() {
                return DataCenterInfo.Name.MyOwn;
            }
        };
        InstanceInfo instanceInfo = InstanceInfo.Builder.newBuilder()
                .setAppName("test")
                .setVIPAddress("test.testname:1")
                .setSecureVIPAddress("securetest.testname:7102")
                .setDataCenterInfo(myDCI)
                .setHostName("test.hostname")
                .setStatus(InstanceStatus.UP)
                .build();

        Application application = new Application("TestApp");
        application.addInstance(instanceInfo);

        Applications applications = new Applications();
        applications.addApplication(application);

        assertNotNull(applications.getNextIndex("test.testname:1", false));
        assertEquals(0L, applications.getNextIndex("test.testname:1", false).get());
        assertNotNull(applications.getNextIndex("securetest.testname:7102", true));
        assertEquals(0L, applications.getNextIndex("securetest.testname:7102", true).get());
        assertNotSame(applications.getNextIndex("test.testname:1", false), applications.getNextIndex("securetest.testname:7102", true));
    }
    
    @Test
    public void testReconcileHashcode() {
        DataCenterInfo myDCI = new DataCenterInfo() {
            public DataCenterInfo.Name getName() {
                return DataCenterInfo.Name.MyOwn;
            }
        };
        InstanceInfo instanceInfo = InstanceInfo.Builder.newBuilder()
                .setAppName("test")
                .setVIPAddress("test.testname:1")
                .setSecureVIPAddress("securetest.testname:7102")
                .setDataCenterInfo(myDCI)
                .setHostName("test.hostname")
                .setStatus(InstanceStatus.UP)
                .build();

        Application application = new Application("TestApp");
        application.addInstance(instanceInfo);

        Applications applications = new Applications();

        String hashCode = applications.getReconcileHashCode();
        assertTrue(hashCode.isEmpty());
        
        applications.addApplication(application);
        hashCode = applications.getReconcileHashCode();
        assertFalse(hashCode.isEmpty());
        assertEquals("UP_1_", hashCode);
    }
    
    @Test
    public void testInstanceFiltering() {
        DataCenterInfo myDCI = new DataCenterInfo() {
            public DataCenterInfo.Name getName() {
                return DataCenterInfo.Name.MyOwn;
            }
        };
        InstanceInfo instanceInfo = InstanceInfo.Builder.newBuilder()
                .setAppName("test")
                .setVIPAddress("test.testname:1")
                .setSecureVIPAddress("securetest.testname:7102")
                .setDataCenterInfo(myDCI)
                .setHostName("test.hostname")
                .setStatus(InstanceStatus.DOWN)
                .build();

        Application application = new Application("TestApp");
        application.addInstance(instanceInfo);

        Applications applications = new Applications();
        applications.addApplication(application);
        applications.shuffleInstances(true);

        assertNotNull(applications.getRegisteredApplications("TestApp").getByInstanceId("test.hostname"));
        assertTrue(applications.getInstancesBySecureVirtualHostName("securetest.testname:7102").isEmpty());
        assertTrue(applications.getInstancesBySecureVirtualHostName("test.testname:1").isEmpty());
    }

    // ==================== VIP Address Parsing Tests ====================

    private static final DataCenterInfo TEST_DCI = () -> DataCenterInfo.Name.MyOwn;

    @Test
    public void testVipParsing_nullVip() {
        Application app = new Application("TestApp");
        Applications apps = new Applications();
        apps.addApplication(app);
        apps.shuffleInstances(false);

        // No instances added
        assertEquals(0, apps.getRegisteredApplications("TestApp").size());
        assertEquals(0, apps.getRegisteredApplications("TestApp").getInstancesAsIsFromEureka().size());
        // No VIP mappings exist
        assertTrue(apps.getInstancesByVirtualHostName("anything").isEmpty());
    }

    @Test
    public void testVipParsing_emptyVip() {
        InstanceInfo instance = InstanceInfo.Builder.newBuilder()
                .setAppName("test").setVIPAddress("").setDataCenterInfo(TEST_DCI)
                .setHostName("host1").setStatus(InstanceStatus.UP).build();
        Application app = new Application("TestApp");
        app.addInstance(instance);
        Applications apps = new Applications();
        apps.addApplication(app);
        apps.shuffleInstances(false);

        // Instance exists in application
        assertEquals(1, apps.getRegisteredApplications("TestApp").size());
        assertEquals(1, apps.getRegisteredApplications("TestApp").getInstancesAsIsFromEureka().size());
        // Legacy behavior: empty VIP creates a mapping with empty string key
        assertEquals(1, apps.getInstancesByVirtualHostName("").size());
    }

    @Test
    public void testVipParsing_singleVip() {
        InstanceInfo instance = InstanceInfo.Builder.newBuilder()
                .setAppName("test").setVIPAddress("my.vip").setDataCenterInfo(TEST_DCI)
                .setHostName("host1").setStatus(InstanceStatus.UP).build();
        Application app = new Application("TestApp");
        app.addInstance(instance);
        Applications apps = new Applications();
        apps.addApplication(app);
        apps.shuffleInstances(false);

        assertEquals(1, apps.getRegisteredApplications("TestApp").size());
        assertEquals(1, apps.getRegisteredApplications("TestApp").getInstancesAsIsFromEureka().size());
        assertEquals(1, apps.getInstancesByVirtualHostName("my.vip").size());
        assertEquals(1, apps.getInstancesByVirtualHostName("MY.VIP").size()); // case-insensitive lookup
        assertTrue(apps.getInstancesByVirtualHostName("other.vip").isEmpty()); // no other entries
    }

    @Test
    public void testVipParsing_singleVipMixedCase() {
        InstanceInfo instance = InstanceInfo.Builder.newBuilder()
                .setAppName("test").setVIPAddress("My.Vip.Address").setDataCenterInfo(TEST_DCI)
                .setHostName("host1").setStatus(InstanceStatus.UP).build();
        Application app = new Application("TestApp");
        app.addInstance(instance);
        Applications apps = new Applications();
        apps.addApplication(app);
        apps.shuffleInstances(false);

        assertEquals(1, apps.getRegisteredApplications("TestApp").size());
        assertEquals(1, apps.getRegisteredApplications("TestApp").getInstancesAsIsFromEureka().size());
        // All case variations should resolve to same entry
        assertEquals(1, apps.getInstancesByVirtualHostName("my.vip.address").size());
        assertEquals(1, apps.getInstancesByVirtualHostName("MY.VIP.ADDRESS").size());
        assertEquals(1, apps.getInstancesByVirtualHostName("My.Vip.Address").size());
    }

    @Test
    public void testVipParsing_twoVips() {
        InstanceInfo instance = InstanceInfo.Builder.newBuilder()
                .setAppName("test").setVIPAddress("vip1,vip2").setDataCenterInfo(TEST_DCI)
                .setHostName("host1").setStatus(InstanceStatus.UP).build();
        Application app = new Application("TestApp");
        app.addInstance(instance);
        Applications apps = new Applications();
        apps.addApplication(app);
        apps.shuffleInstances(false);

        // One instance in the application
        assertEquals(1, apps.getRegisteredApplications("TestApp").size());
        assertEquals(1, apps.getRegisteredApplications("TestApp").getInstancesAsIsFromEureka().size());
        // Mapped to two VIPs
        assertEquals(1, apps.getInstancesByVirtualHostName("vip1").size());
        assertEquals(1, apps.getInstancesByVirtualHostName("vip2").size());
        // No spurious entries
        assertTrue(apps.getInstancesByVirtualHostName("vip1,vip2").isEmpty());
        assertTrue(apps.getInstancesByVirtualHostName("vip3").isEmpty());
    }

    @Test
    public void testVipParsing_threeVips() {
        InstanceInfo instance = InstanceInfo.Builder.newBuilder()
                .setAppName("test").setVIPAddress("vip1,vip2,vip3").setDataCenterInfo(TEST_DCI)
                .setHostName("host1").setStatus(InstanceStatus.UP).build();
        Application app = new Application("TestApp");
        app.addInstance(instance);
        Applications apps = new Applications();
        apps.addApplication(app);
        apps.shuffleInstances(false);

        assertEquals(1, apps.getRegisteredApplications("TestApp").size());
        assertEquals(1, apps.getRegisteredApplications("TestApp").getInstancesAsIsFromEureka().size());
        assertEquals(1, apps.getInstancesByVirtualHostName("vip1").size());
        assertEquals(1, apps.getInstancesByVirtualHostName("vip2").size());
        assertEquals(1, apps.getInstancesByVirtualHostName("vip3").size());
        // No spurious entries from parsing
        assertTrue(apps.getInstancesByVirtualHostName("vip1,vip2").isEmpty());
        assertTrue(apps.getInstancesByVirtualHostName("vip2,vip3").isEmpty());
        assertTrue(apps.getInstancesByVirtualHostName("vip1,vip2,vip3").isEmpty());
    }

    @Test
    public void testVipParsing_multipleVipsMixedCase() {
        InstanceInfo instance = InstanceInfo.Builder.newBuilder()
                .setAppName("test").setVIPAddress("Vip.One,VIP.TWO,vip.three").setDataCenterInfo(TEST_DCI)
                .setHostName("host1").setStatus(InstanceStatus.UP).build();
        Application app = new Application("TestApp");
        app.addInstance(instance);
        Applications apps = new Applications();
        apps.addApplication(app);
        apps.shuffleInstances(false);

        assertEquals(1, apps.getRegisteredApplications("TestApp").size());
        assertEquals(1, apps.getRegisteredApplications("TestApp").getInstancesAsIsFromEureka().size());
        // All stored as uppercase, lookup case-insensitive
        assertEquals(1, apps.getInstancesByVirtualHostName("vip.one").size());
        assertEquals(1, apps.getInstancesByVirtualHostName("VIP.ONE").size());
        assertEquals(1, apps.getInstancesByVirtualHostName("vip.two").size());
        assertEquals(1, apps.getInstancesByVirtualHostName("vip.three").size());
    }

    @Test
    public void testVipParsing_multipleInstancesOverlappingVips() {
        // host1: vip.one, vip.two, vip.three
        // host2: vip.two, vip.four
        // host3: vip.three, vip.four, vip.five
        InstanceInfo host1 = InstanceInfo.Builder.newBuilder()
                .setAppName("test").setVIPAddress("vip.one,vip.two,vip.three").setDataCenterInfo(TEST_DCI)
                .setHostName("host1").setStatus(InstanceStatus.UP).build();
        InstanceInfo host2 = InstanceInfo.Builder.newBuilder()
                .setAppName("test").setVIPAddress("vip.two,vip.four").setDataCenterInfo(TEST_DCI)
                .setHostName("host2").setStatus(InstanceStatus.UP).build();
        InstanceInfo host3 = InstanceInfo.Builder.newBuilder()
                .setAppName("test").setVIPAddress("vip.three,vip.four,vip.five").setDataCenterInfo(TEST_DCI)
                .setHostName("host3").setStatus(InstanceStatus.UP).build();

        Application app = new Application("TestApp");
        app.addInstance(host1);
        app.addInstance(host2);
        app.addInstance(host3);
        Applications apps = new Applications();
        apps.addApplication(app);
        apps.shuffleInstances(false);

        // Verify application contains all 3 instances
        assertEquals(3, apps.getRegisteredApplications("TestApp").size());
        assertEquals(3, apps.getRegisteredApplications("TestApp").getInstancesAsIsFromEureka().size());

        // vip.one -> host1 only
        List<InstanceInfo> vipOneInstances = apps.getInstancesByVirtualHostName("vip.one");
        assertEquals(1, vipOneInstances.size());
        assertEquals("host1", vipOneInstances.get(0).getHostName());

        // vip.two -> host1, host2
        List<InstanceInfo> vipTwoInstances = apps.getInstancesByVirtualHostName("vip.two");
        assertEquals(2, vipTwoInstances.size());
        assertTrue(vipTwoInstances.stream().anyMatch(i -> "host1".equals(i.getHostName())));
        assertTrue(vipTwoInstances.stream().anyMatch(i -> "host2".equals(i.getHostName())));

        // vip.three -> host1, host3
        List<InstanceInfo> vipThreeInstances = apps.getInstancesByVirtualHostName("vip.three");
        assertEquals(2, vipThreeInstances.size());
        assertTrue(vipThreeInstances.stream().anyMatch(i -> "host1".equals(i.getHostName())));
        assertTrue(vipThreeInstances.stream().anyMatch(i -> "host3".equals(i.getHostName())));

        // vip.four -> host2, host3
        List<InstanceInfo> vipFourInstances = apps.getInstancesByVirtualHostName("vip.four");
        assertEquals(2, vipFourInstances.size());
        assertTrue(vipFourInstances.stream().anyMatch(i -> "host2".equals(i.getHostName())));
        assertTrue(vipFourInstances.stream().anyMatch(i -> "host3".equals(i.getHostName())));

        // vip.five -> host3 only
        List<InstanceInfo> vipFiveInstances = apps.getInstancesByVirtualHostName("vip.five");
        assertEquals(1, vipFiveInstances.size());
        assertEquals("host3", vipFiveInstances.get(0).getHostName());

        // Case-insensitive lookups work
        assertEquals(2, apps.getInstancesByVirtualHostName("VIP.TWO").size());
        assertEquals(2, apps.getInstancesByVirtualHostName("VIP.FOUR").size());

        // No spurious VIP entries
        assertTrue(apps.getInstancesByVirtualHostName("vip.six").isEmpty());
        assertTrue(apps.getInstancesByVirtualHostName("vip.one,vip.two").isEmpty());
    }

    // ==================== shuffleAndFilterInstances Tests ====================

    @Test
    public void testMultipleInstancesFiltering() {
        DataCenterInfo myDCI = new DataCenterInfo() {
            public DataCenterInfo.Name getName() {
                return DataCenterInfo.Name.MyOwn;
            }
        };
        InstanceInfo up1 = InstanceInfo.Builder.newBuilder().setAppName("test")
                .setVIPAddress("test.vip").setDataCenterInfo(myDCI)
                .setHostName("up1.hostname").setStatus(InstanceStatus.UP).build();
        InstanceInfo up2 = InstanceInfo.Builder.newBuilder().setAppName("test")
                .setVIPAddress("test.vip").setDataCenterInfo(myDCI)
                .setHostName("up2.hostname").setStatus(InstanceStatus.UP).build();
        InstanceInfo down = InstanceInfo.Builder.newBuilder().setAppName("test")
                .setVIPAddress("test.vip").setDataCenterInfo(myDCI)
                .setHostName("down.hostname").setStatus(InstanceStatus.DOWN).build();

        Application application = new Application("TestApp");
        application.addInstance(up1);
        application.addInstance(up2);
        application.addInstance(down);

        Applications applications = new Applications();
        applications.addApplication(application);
        applications.shuffleInstances(true);

        List<InstanceInfo> result = applications.getInstancesByVirtualHostName("test.vip");
        assertEquals(2, result.size());
        assertTrue(result.contains(up1));
        assertTrue(result.contains(up2));
        assertFalse(result.contains(down));
    }

    @Test
    public void testSingleInstanceUp_filterEnabled() {
        InstanceInfo instance = InstanceInfo.Builder.newBuilder()
                .setAppName("test").setVIPAddress("my.vip").setDataCenterInfo(TEST_DCI)
                .setHostName("host1").setStatus(InstanceStatus.UP).build();
        Application app = new Application("TestApp");
        app.addInstance(instance);
        Applications apps = new Applications();
        apps.addApplication(app);
        apps.shuffleInstances(true);

        List<InstanceInfo> result = apps.getInstancesByVirtualHostName("my.vip");
        assertEquals(1, result.size());
        assertTrue(result.contains(instance));
    }

    @Test
    public void testSingleInstanceDown_filterEnabled() {
        InstanceInfo instance = InstanceInfo.Builder.newBuilder()
                .setAppName("test").setVIPAddress("my.vip").setDataCenterInfo(TEST_DCI)
                .setHostName("host1").setStatus(InstanceStatus.DOWN).build();
        Application app = new Application("TestApp");
        app.addInstance(instance);
        Applications apps = new Applications();
        apps.addApplication(app);
        apps.shuffleInstances(true);

        // Single DOWN instance should be filtered out
        List<InstanceInfo> result = apps.getInstancesByVirtualHostName("my.vip");
        assertTrue(result.isEmpty());
        // But instance still exists in the application
        assertEquals(1, apps.getRegisteredApplications("TestApp").size());
    }

    @Test
    public void testMultipleInstances_filterDisabled() {
        InstanceInfo up = InstanceInfo.Builder.newBuilder()
                .setAppName("test").setVIPAddress("my.vip").setDataCenterInfo(TEST_DCI)
                .setHostName("up.host").setStatus(InstanceStatus.UP).build();
        InstanceInfo down = InstanceInfo.Builder.newBuilder()
                .setAppName("test").setVIPAddress("my.vip").setDataCenterInfo(TEST_DCI)
                .setHostName("down.host").setStatus(InstanceStatus.DOWN).build();
        InstanceInfo oos = InstanceInfo.Builder.newBuilder()
                .setAppName("test").setVIPAddress("my.vip").setDataCenterInfo(TEST_DCI)
                .setHostName("oos.host").setStatus(InstanceStatus.OUT_OF_SERVICE).build();

        Application app = new Application("TestApp");
        app.addInstance(up);
        app.addInstance(down);
        app.addInstance(oos);
        Applications apps = new Applications();
        apps.addApplication(app);
        apps.shuffleInstances(false); // filterUpInstances = false

        // All instances should be present regardless of status
        List<InstanceInfo> result = apps.getInstancesByVirtualHostName("my.vip");
        assertEquals(3, result.size());
        assertTrue(result.contains(up));
        assertTrue(result.contains(down));
        assertTrue(result.contains(oos));
    }

    @Test
    public void testAllInstancesNonUp_filterEnabled() {
        InstanceInfo down = InstanceInfo.Builder.newBuilder()
                .setAppName("test").setVIPAddress("my.vip").setDataCenterInfo(TEST_DCI)
                .setHostName("down.host").setStatus(InstanceStatus.DOWN).build();
        InstanceInfo oos = InstanceInfo.Builder.newBuilder()
                .setAppName("test").setVIPAddress("my.vip").setDataCenterInfo(TEST_DCI)
                .setHostName("oos.host").setStatus(InstanceStatus.OUT_OF_SERVICE).build();
        InstanceInfo starting = InstanceInfo.Builder.newBuilder()
                .setAppName("test").setVIPAddress("my.vip").setDataCenterInfo(TEST_DCI)
                .setHostName("starting.host").setStatus(InstanceStatus.STARTING).build();

        Application app = new Application("TestApp");
        app.addInstance(down);
        app.addInstance(oos);
        app.addInstance(starting);
        Applications apps = new Applications();
        apps.addApplication(app);
        apps.shuffleInstances(true);

        // All instances filtered out
        List<InstanceInfo> result = apps.getInstancesByVirtualHostName("my.vip");
        assertTrue(result.isEmpty());
        // But all instances still exist in the application
        assertEquals(3, apps.getRegisteredApplications("TestApp").size());
    }

    @Test
    public void testSecureVipFiltering() {
        InstanceInfo up = InstanceInfo.Builder.newBuilder()
                .setAppName("test").setSecureVIPAddress("secure.vip").setDataCenterInfo(TEST_DCI)
                .setHostName("up.host").setStatus(InstanceStatus.UP).build();
        InstanceInfo down = InstanceInfo.Builder.newBuilder()
                .setAppName("test").setSecureVIPAddress("secure.vip").setDataCenterInfo(TEST_DCI)
                .setHostName("down.host").setStatus(InstanceStatus.DOWN).build();

        Application app = new Application("TestApp");
        app.addInstance(up);
        app.addInstance(down);
        Applications apps = new Applications();
        apps.addApplication(app);
        apps.shuffleInstances(true);

        List<InstanceInfo> result = apps.getInstancesBySecureVirtualHostName("secure.vip");
        assertEquals(1, result.size());
        assertTrue(result.contains(up));
        assertFalse(result.contains(down));
    }

    @Test
    public void testReshuffleUpdatesFilteredList() {
        InstanceInfo host1 = InstanceInfo.Builder.newBuilder()
                .setAppName("test").setVIPAddress("my.vip").setDataCenterInfo(TEST_DCI)
                .setHostName("host1").setStatus(InstanceStatus.UP).build();
        InstanceInfo host2 = InstanceInfo.Builder.newBuilder()
                .setAppName("test").setVIPAddress("my.vip").setDataCenterInfo(TEST_DCI)
                .setHostName("host2").setStatus(InstanceStatus.UP).build();

        Application app = new Application("TestApp");
        app.addInstance(host1);
        app.addInstance(host2);
        Applications apps = new Applications();
        apps.addApplication(app);
        apps.shuffleInstances(true);

        assertEquals(2, apps.getInstancesByVirtualHostName("my.vip").size());

        // Change host2 to DOWN and reshuffle
        host2.setStatus(InstanceStatus.DOWN);
        apps.shuffleInstances(true);

        // Only host1 should remain
        List<InstanceInfo> result = apps.getInstancesByVirtualHostName("my.vip");
        assertEquals(1, result.size());
        assertTrue(result.contains(host1));
        assertFalse(result.contains(host2));
    }

    @Test
    public void testMixedVipAndSecureVipFiltering() {
        InstanceInfo upBoth = InstanceInfo.Builder.newBuilder()
                .setAppName("test").setVIPAddress("my.vip").setSecureVIPAddress("secure.vip")
                .setDataCenterInfo(TEST_DCI).setHostName("up.both").setStatus(InstanceStatus.UP).build();
        InstanceInfo downBoth = InstanceInfo.Builder.newBuilder()
                .setAppName("test").setVIPAddress("my.vip").setSecureVIPAddress("secure.vip")
                .setDataCenterInfo(TEST_DCI).setHostName("down.both").setStatus(InstanceStatus.DOWN).build();
        InstanceInfo upVipOnly = InstanceInfo.Builder.newBuilder()
                .setAppName("test").setVIPAddress("my.vip")
                .setDataCenterInfo(TEST_DCI).setHostName("up.vip").setStatus(InstanceStatus.UP).build();

        Application app = new Application("TestApp");
        app.addInstance(upBoth);
        app.addInstance(downBoth);
        app.addInstance(upVipOnly);
        Applications apps = new Applications();
        apps.addApplication(app);
        apps.shuffleInstances(true);

        // VIP should have 2 UP instances
        List<InstanceInfo> vipResult = apps.getInstancesByVirtualHostName("my.vip");
        assertEquals(2, vipResult.size());
        assertTrue(vipResult.contains(upBoth));
        assertTrue(vipResult.contains(upVipOnly));

        // Secure VIP should have 1 UP instance
        List<InstanceInfo> secureResult = apps.getInstancesBySecureVirtualHostName("secure.vip");
        assertEquals(1, secureResult.size());
        assertTrue(secureResult.contains(upBoth));
    }

    // ==================== Collection Progression Tests (emptyList -> singletonList -> ArrayList) ====================

    @Test
    public void testVipInstanceCountProgression() {
        // Explicitly test 0->1->2->3->4 instance progression on same VIP
        // Validates emptyList -> singletonList -> ArrayList(12) transitions
        InstanceInfo inst1 = InstanceInfo.Builder.newBuilder()
                .setAppName("test").setVIPAddress("my.vip").setDataCenterInfo(TEST_DCI)
                .setHostName("host1").setStatus(InstanceStatus.UP).build();
        InstanceInfo inst2 = InstanceInfo.Builder.newBuilder()
                .setAppName("test").setVIPAddress("my.vip").setDataCenterInfo(TEST_DCI)
                .setHostName("host2").setStatus(InstanceStatus.UP).build();
        InstanceInfo inst3 = InstanceInfo.Builder.newBuilder()
                .setAppName("test").setVIPAddress("my.vip").setDataCenterInfo(TEST_DCI)
                .setHostName("host3").setStatus(InstanceStatus.UP).build();
        InstanceInfo inst4 = InstanceInfo.Builder.newBuilder()
                .setAppName("test").setVIPAddress("my.vip").setDataCenterInfo(TEST_DCI)
                .setHostName("host4").setStatus(InstanceStatus.UP).build();

        Application app = new Application("TestApp");
        Applications apps = new Applications();

        // 0 instances - no VIP entry exists yet
        apps.addApplication(app);
        apps.shuffleInstances(false);
        assertTrue(apps.getInstancesByVirtualHostName("my.vip").isEmpty());

        // 1 instance - singletonList path
        app.addInstance(inst1);
        apps = new Applications();
        apps.addApplication(app);
        apps.shuffleInstances(false);
        List<InstanceInfo> result1 = apps.getInstancesByVirtualHostName("my.vip");
        assertEquals(1, result1.size());
        assertTrue(result1.contains(inst1));

        // 2 instances - ArrayList transition
        app.addInstance(inst2);
        apps = new Applications();
        apps.addApplication(app);
        apps.shuffleInstances(false);
        List<InstanceInfo> result2 = apps.getInstancesByVirtualHostName("my.vip");
        assertEquals(2, result2.size());
        assertTrue(result2.contains(inst1));
        assertTrue(result2.contains(inst2));

        // 3 instances - ArrayList grows
        app.addInstance(inst3);
        apps = new Applications();
        apps.addApplication(app);
        apps.shuffleInstances(false);
        List<InstanceInfo> result3 = apps.getInstancesByVirtualHostName("my.vip");
        assertEquals(3, result3.size());

        // 4 instances - ArrayList continues to grow
        app.addInstance(inst4);
        apps = new Applications();
        apps.addApplication(app);
        apps.shuffleInstances(false);
        List<InstanceInfo> result4 = apps.getInstancesByVirtualHostName("my.vip");
        assertEquals(4, result4.size());
        assertTrue(result4.contains(inst1));
        assertTrue(result4.contains(inst2));
        assertTrue(result4.contains(inst3));
        assertTrue(result4.contains(inst4));
    }

    @Test
    public void testFilteringWithSingletonList_instanceUp() {
        // Single UP instance: singletonList should be preserved
        InstanceInfo instance = InstanceInfo.Builder.newBuilder()
                .setAppName("test").setVIPAddress("my.vip").setDataCenterInfo(TEST_DCI)
                .setHostName("host1").setStatus(InstanceStatus.UP).build();
        Application app = new Application("TestApp");
        app.addInstance(instance);
        Applications apps = new Applications();
        apps.addApplication(app);
        apps.shuffleInstances(true);

        List<InstanceInfo> result = apps.getInstancesByVirtualHostName("my.vip");
        assertEquals(1, result.size());
        assertTrue(result.contains(instance));
    }

    @Test
    public void testFilteringWithSingletonList_instanceDown() {
        // Single DOWN instance: should return emptyList
        InstanceInfo instance = InstanceInfo.Builder.newBuilder()
                .setAppName("test").setVIPAddress("my.vip").setDataCenterInfo(TEST_DCI)
                .setHostName("host1").setStatus(InstanceStatus.DOWN).build();
        Application app = new Application("TestApp");
        app.addInstance(instance);
        Applications apps = new Applications();
        apps.addApplication(app);
        apps.shuffleInstances(true);

        List<InstanceInfo> result = apps.getInstancesByVirtualHostName("my.vip");
        assertTrue(result.isEmpty());
    }

    @Test
    public void testFilteringWithArrayList_oneUpOneDown() {
        // 2 instances (ArrayList), 1 UP 1 DOWN: filters to 1
        InstanceInfo up = InstanceInfo.Builder.newBuilder()
                .setAppName("test").setVIPAddress("my.vip").setDataCenterInfo(TEST_DCI)
                .setHostName("up.host").setStatus(InstanceStatus.UP).build();
        InstanceInfo down = InstanceInfo.Builder.newBuilder()
                .setAppName("test").setVIPAddress("my.vip").setDataCenterInfo(TEST_DCI)
                .setHostName("down.host").setStatus(InstanceStatus.DOWN).build();
        Application app = new Application("TestApp");
        app.addInstance(up);
        app.addInstance(down);
        Applications apps = new Applications();
        apps.addApplication(app);
        apps.shuffleInstances(true);

        List<InstanceInfo> result = apps.getInstancesByVirtualHostName("my.vip");
        assertEquals(1, result.size());
        assertTrue(result.contains(up));
        assertFalse(result.contains(down));
    }

    @Test
    public void testFilteringWithArrayList_allDown() {
        // 3 instances (ArrayList), all DOWN: should return emptyList
        InstanceInfo down1 = InstanceInfo.Builder.newBuilder()
                .setAppName("test").setVIPAddress("my.vip").setDataCenterInfo(TEST_DCI)
                .setHostName("down1.host").setStatus(InstanceStatus.DOWN).build();
        InstanceInfo down2 = InstanceInfo.Builder.newBuilder()
                .setAppName("test").setVIPAddress("my.vip").setDataCenterInfo(TEST_DCI)
                .setHostName("down2.host").setStatus(InstanceStatus.OUT_OF_SERVICE).build();
        InstanceInfo down3 = InstanceInfo.Builder.newBuilder()
                .setAppName("test").setVIPAddress("my.vip").setDataCenterInfo(TEST_DCI)
                .setHostName("down3.host").setStatus(InstanceStatus.STARTING).build();
        Application app = new Application("TestApp");
        app.addInstance(down1);
        app.addInstance(down2);
        app.addInstance(down3);
        Applications apps = new Applications();
        apps.addApplication(app);
        apps.shuffleInstances(true);

        List<InstanceInfo> result = apps.getInstancesByVirtualHostName("my.vip");
        assertTrue(result.isEmpty());
    }

    @Test
    public void testFilteringPreservesUpInstancesInOrder() {
        // Verify UP instances are preserved (order may change due to shuffle)
        InstanceInfo up1 = InstanceInfo.Builder.newBuilder()
                .setAppName("test").setVIPAddress("my.vip").setDataCenterInfo(TEST_DCI)
                .setHostName("up1").setStatus(InstanceStatus.UP).build();
        InstanceInfo down1 = InstanceInfo.Builder.newBuilder()
                .setAppName("test").setVIPAddress("my.vip").setDataCenterInfo(TEST_DCI)
                .setHostName("down1").setStatus(InstanceStatus.DOWN).build();
        InstanceInfo up2 = InstanceInfo.Builder.newBuilder()
                .setAppName("test").setVIPAddress("my.vip").setDataCenterInfo(TEST_DCI)
                .setHostName("up2").setStatus(InstanceStatus.UP).build();
        InstanceInfo down2 = InstanceInfo.Builder.newBuilder()
                .setAppName("test").setVIPAddress("my.vip").setDataCenterInfo(TEST_DCI)
                .setHostName("down2").setStatus(InstanceStatus.OUT_OF_SERVICE).build();
        InstanceInfo up3 = InstanceInfo.Builder.newBuilder()
                .setAppName("test").setVIPAddress("my.vip").setDataCenterInfo(TEST_DCI)
                .setHostName("up3").setStatus(InstanceStatus.UP).build();

        Application app = new Application("TestApp");
        app.addInstance(up1);
        app.addInstance(down1);
        app.addInstance(up2);
        app.addInstance(down2);
        app.addInstance(up3);
        Applications apps = new Applications();
        apps.addApplication(app);
        apps.shuffleInstances(true);

        List<InstanceInfo> result = apps.getInstancesByVirtualHostName("my.vip");
        assertEquals(3, result.size());
        assertTrue(result.contains(up1));
        assertTrue(result.contains(up2));
        assertTrue(result.contains(up3));
        assertFalse(result.contains(down1));
        assertFalse(result.contains(down2));
    }

    @Test
    public void testInstanceRemovalFromApplication() {
        // Test that removing an instance from application is reflected after reshuffle
        InstanceInfo inst1 = InstanceInfo.Builder.newBuilder()
                .setAppName("test").setVIPAddress("my.vip").setDataCenterInfo(TEST_DCI)
                .setHostName("host1").setStatus(InstanceStatus.UP).build();
        InstanceInfo inst2 = InstanceInfo.Builder.newBuilder()
                .setAppName("test").setVIPAddress("my.vip").setDataCenterInfo(TEST_DCI)
                .setHostName("host2").setStatus(InstanceStatus.UP).build();
        InstanceInfo inst3 = InstanceInfo.Builder.newBuilder()
                .setAppName("test").setVIPAddress("my.vip").setDataCenterInfo(TEST_DCI)
                .setHostName("host3").setStatus(InstanceStatus.UP).build();

        Application app = new Application("TestApp");
        app.addInstance(inst1);
        app.addInstance(inst2);
        app.addInstance(inst3);
        Applications apps = new Applications();
        apps.addApplication(app);
        apps.shuffleInstances(false);

        assertEquals(3, apps.getInstancesByVirtualHostName("my.vip").size());

        // Remove one instance
        app.removeInstance(inst2);
        apps.shuffleInstances(false);

        List<InstanceInfo> result = apps.getInstancesByVirtualHostName("my.vip");
        assertEquals(2, result.size());
        assertTrue(result.contains(inst1));
        assertFalse(result.contains(inst2));
        assertTrue(result.contains(inst3));
    }

    @Test
    public void testRemoveAllInstancesFromVip() {
        // Test removing all instances results in empty VIP list
        InstanceInfo inst1 = InstanceInfo.Builder.newBuilder()
                .setAppName("test").setVIPAddress("my.vip").setDataCenterInfo(TEST_DCI)
                .setHostName("host1").setStatus(InstanceStatus.UP).build();
        InstanceInfo inst2 = InstanceInfo.Builder.newBuilder()
                .setAppName("test").setVIPAddress("my.vip").setDataCenterInfo(TEST_DCI)
                .setHostName("host2").setStatus(InstanceStatus.UP).build();

        Application app = new Application("TestApp");
        app.addInstance(inst1);
        app.addInstance(inst2);
        Applications apps = new Applications();
        apps.addApplication(app);
        apps.shuffleInstances(false);

        assertEquals(2, apps.getInstancesByVirtualHostName("my.vip").size());

        // Remove all instances
        app.removeInstance(inst1);
        app.removeInstance(inst2);
        apps.shuffleInstances(false);

        assertTrue(apps.getInstancesByVirtualHostName("my.vip").isEmpty());
    }

}
