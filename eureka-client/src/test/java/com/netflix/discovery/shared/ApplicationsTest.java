package com.netflix.discovery.shared;


import java.util.List;

import com.google.common.collect.Iterables;
import com.netflix.appinfo.DataCenterInfo;
import com.netflix.appinfo.InstanceInfo;
import org.junit.Test;

import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

public class ApplicationsTest {

    /**
     * Test that instancesMap in Application and shuffleVirtualHostNameMap in Applications are
     * correctly updated when the last instance is removed from an application and shuffleInstances
     * has been run.
     */
    @Test
    public void shuffleVirtualHostNameMapLastInstanceTest() {
        DataCenterInfo myDCI = new DataCenterInfo() {
            public DataCenterInfo.Name getName() {
                return DataCenterInfo.Name.MyOwn;
            }
        };
        InstanceInfo instanceInfo = InstanceInfo.Builder.newBuilder()
                .setAppName("test")
                .setVIPAddress("test.testname:1")
                .setDataCenterInfo(myDCI)
                .setHostName("test.hostname").build();

        Application application = new Application("TestApp");
        application.addInstance(instanceInfo);
        Applications applications = new Applications();
        applications.addApplication(application);
        applications.shuffleInstances(true);
        List<InstanceInfo> testApp = applications.getInstancesByVirtualHostName("test.testname:1");

        assertEquals(Iterables.getOnlyElement(testApp),
                application.getByInstanceId("test.hostname"));

        application.removeInstance(instanceInfo);
        applications.shuffleInstances(true);
        testApp = applications.getInstancesByVirtualHostName("test.testname:1");

        assertNull(application.getByInstanceId("test.hostname"));
        assertTrue(testApp.isEmpty());
    }
}
