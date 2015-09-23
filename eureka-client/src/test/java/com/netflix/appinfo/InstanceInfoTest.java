package com.netflix.appinfo;

import com.netflix.appinfo.InstanceInfo.Builder;
import com.netflix.appinfo.InstanceInfo.PortType;
import com.netflix.config.ConcurrentCompositeConfiguration;
import com.netflix.config.ConfigurationManager;
import com.netflix.discovery.util.InstanceInfoGenerator;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import static com.netflix.appinfo.InstanceInfo.Builder.newBuilder;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertThat;

/**
 * Created by jzarfoss on 2/12/14.
 */
public class InstanceInfoTest {
    @After
    public void tearDown() throws Exception {
        ((ConcurrentCompositeConfiguration) ConfigurationManager.getConfigInstance()).clearOverrideProperty("NETFLIX_APP_GROUP");
        ((ConcurrentCompositeConfiguration) ConfigurationManager.getConfigInstance()).clearOverrideProperty("eureka.appGroup");
    }

    // contrived test to check copy constructor and verify behavior of builder for InstanceInfo

    @Test
    public void testCopyConstructor() {

        DataCenterInfo myDCI = new DataCenterInfo() {

            public DataCenterInfo.Name getName() {
                return DataCenterInfo.Name.MyOwn;
            }

        };


        InstanceInfo smallII1 = newBuilder().setAppName("test").setDataCenterInfo(myDCI).build();
        InstanceInfo smallII2 = new InstanceInfo(smallII1);

        assertNotSame(smallII1, smallII2);
        Assert.assertEquals(smallII1, smallII2);


        InstanceInfo fullII1 = newBuilder().setMetadata(null)
                .setOverriddenStatus(InstanceInfo.InstanceStatus.UNKNOWN)
                .setHostName("localhost")
                .setSecureVIPAddress("testSecureVIP:22")
                .setStatus(InstanceInfo.InstanceStatus.UNKNOWN)
                .setStatusPageUrl("relative", "explicit/relative")
                .setVIPAddress("testVIP:21")
                .setAppName("test").setASGName("testASG").setDataCenterInfo(myDCI)
                .setHealthCheckUrls("relative", "explicit/relative", "secureExplicit/relative")
                .setHomePageUrl("relativeHP", "explicitHP/relativeHP")
                .setIPAddr("127.0.0.1")
                .setPort(21).setSecurePort(22).build();

        InstanceInfo fullII2 = new InstanceInfo(fullII1);

        assertNotSame(fullII1, fullII2);
        Assert.assertEquals(fullII1, fullII2);
    }

    @Test
    public void testAppGroupNameSystemProp() throws Exception {
        String appGroup = "testAppGroupSystemProp";
        ((ConcurrentCompositeConfiguration) ConfigurationManager.getConfigInstance()).setOverrideProperty("NETFLIX_APP_GROUP",
                appGroup);
        MyDataCenterInstanceConfig config = new MyDataCenterInstanceConfig();
        Assert.assertEquals("Unexpected app group name", appGroup, config.getAppGroupName());
    }

    @Test
    public void testAppGroupName() throws Exception {
        String appGroup = "testAppGroup";
        ((ConcurrentCompositeConfiguration) ConfigurationManager.getConfigInstance()).setOverrideProperty("eureka.appGroup",
                appGroup);
        MyDataCenterInstanceConfig config = new MyDataCenterInstanceConfig();
        Assert.assertEquals("Unexpected app group name", appGroup, config.getAppGroupName());
    }

    @Test
    public void testHealthCheckSetContainsValidUrlEntries() throws Exception {
        Builder builder = newBuilder()
                .setAppName("test")
                .setNamespace("eureka.")
                .setHostName("localhost")
                .setPort(80)
                .setSecurePort(443)
                .enablePort(PortType.SECURE, true);

        // No health check URLs
        InstanceInfo noHealtcheckInstanceInfo = builder.build();
        assertThat(noHealtcheckInstanceInfo.getHealthCheckUrls().size(), is(equalTo(0)));

        // Now when health check is defined
        InstanceInfo instanceInfo = builder
                .setHealthCheckUrls("/healthcheck", "http://${eureka.hostname}/healthcheck", "https://${eureka.hostname}/healthcheck")
                .build();
        assertThat(instanceInfo.getHealthCheckUrls().size(), is(equalTo(2)));
    }

    @Test
    public void testGetIdWithSIDUsed() {
        InstanceInfo baseline = InstanceInfoGenerator.takeOne();
        String dataCenterInfoId = ((UniqueIdentifier) baseline.getDataCenterInfo()).getId();
        assertThat(baseline.getSID(), is(baseline.getId()));
        assertThat(dataCenterInfoId, is(baseline.getId()));

        String customSid = "customSid";
        InstanceInfo instanceInfo = new InstanceInfo.Builder(baseline).setSID(customSid).build();
        dataCenterInfoId = ((UniqueIdentifier) instanceInfo.getDataCenterInfo()).getId();
        assertThat(instanceInfo.getSID(), is(instanceInfo.getId()));
        assertThat(customSid, is(instanceInfo.getId()));
        assertThat(dataCenterInfoId, is(not(baseline.getId())));
    }

    // test case for compatiblity
    @Test
    public void testGetIdWithSIDNotUsed() {
        String sidDefaultVal = "na";
        InstanceInfo baseline = InstanceInfoGenerator.takeOne();
        InstanceInfo instanceInfo1 = new InstanceInfo.Builder(baseline).setSID(sidDefaultVal).build();
        String dataCenterInfoId = ((UniqueIdentifier) baseline.getDataCenterInfo()).getId();
        assertThat(instanceInfo1.getSID(), is(sidDefaultVal));
        assertThat(instanceInfo1.getSID(), is(not(instanceInfo1.getId())));
        assertThat(dataCenterInfoId, is(instanceInfo1.getId()));

        // override the sid with ""
        InstanceInfo instanceInfo2 = new InstanceInfo.Builder(baseline).setSID("").build();
        dataCenterInfoId = ((UniqueIdentifier) baseline.getDataCenterInfo()).getId();
        assertThat(instanceInfo2.getSID().isEmpty(), is(true));
        assertThat(instanceInfo2.getSID(), is(not(instanceInfo2.getId())));
        assertThat(dataCenterInfoId, is(instanceInfo2.getId()));

        // override the sid with null
        InstanceInfo instanceInfo3 = new InstanceInfo.Builder(baseline).setSID(null).build();
        dataCenterInfoId = ((UniqueIdentifier) baseline.getDataCenterInfo()).getId();
        assertThat(instanceInfo3.getSID(), is(nullValue()));
        assertThat(instanceInfo3.getSID(), is(not(instanceInfo3.getId())));
        assertThat(dataCenterInfoId, is(instanceInfo3.getId()));
    }
}
