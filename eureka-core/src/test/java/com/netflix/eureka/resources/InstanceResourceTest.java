package com.netflix.eureka.resources;

import javax.ws.rs.core.Response;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.eureka.AbstractTester;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class InstanceResourceTest extends AbstractTester {

    private final InstanceInfo testInstanceInfo = createLocalInstance(LOCAL_REGION_INSTANCE_1_HOSTNAME);
    private ApplicationResource applicationResource;
    private InstanceResource instanceResource;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        applicationResource = new ApplicationResource(testInstanceInfo.getAppName());
        instanceResource = new InstanceResource(applicationResource, testInstanceInfo.getId(), registry);
    }

    @Test
    public void testStatusOverrideDeleteIsAppliedToRegistry() throws Exception {
        // Override instance status
        registry.register(testInstanceInfo, false);
        registry.statusUpdate(testInstanceInfo.getAppName(), testInstanceInfo.getId(), InstanceStatus.OUT_OF_SERVICE, "0", false);
        assertThat(testInstanceInfo.getStatus(), is(equalTo(InstanceStatus.OUT_OF_SERVICE)));

        // Remove the override
        Response response = instanceResource.deleteStatusUpdate("false", null, "0");
        assertThat(response.getStatus(), is(equalTo(200)));

        assertThat(testInstanceInfo.getStatus(), is(equalTo(InstanceStatus.UNKNOWN)));
    }

    @Test
    public void testStatusOverrideDeleteIsAppliedToRegistryAndProvidedStatusIsSet() throws Exception {
        // Override instance status
        registry.register(testInstanceInfo, false);
        registry.statusUpdate(testInstanceInfo.getAppName(), testInstanceInfo.getId(), InstanceStatus.OUT_OF_SERVICE, "0", false);
        assertThat(testInstanceInfo.getStatus(), is(equalTo(InstanceStatus.OUT_OF_SERVICE)));

        // Remove the override
        Response response = instanceResource.deleteStatusUpdate("false", "DOWN", "0");
        assertThat(response.getStatus(), is(equalTo(200)));

        assertThat(testInstanceInfo.getStatus(), is(equalTo(InstanceStatus.DOWN)));
    }
}