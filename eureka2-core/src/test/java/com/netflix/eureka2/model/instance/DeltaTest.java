package com.netflix.eureka2.model.instance;

import java.util.HashSet;

import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.testkit.data.builder.SampleServicePort;
import com.netflix.eureka2.utils.ExtCollections;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

/**
 * @author David Liu
 */
public class DeltaTest {

    InstanceInfo original;
    InstanceInfo.Builder instanceInfoBuilder;

    @Rule
    public final ExternalResource testResource = new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            original = SampleInstanceInfo.DiscoveryServer.build();
            instanceInfoBuilder = new InstanceInfo.Builder().withInstanceInfo(original);
        }

    };

    @Test(timeout = 60000)
    public void testSettingFieldOnInstanceInfo_HashSetInt() throws Exception {
        HashSet<ServicePort> newPorts = SampleServicePort.httpPorts();
        Delta<?> delta = new Delta.Builder()
                .withId(original.getId())
                .withDelta(InstanceInfoField.PORTS, newPorts)
                .build();

        InstanceInfo instanceInfo = delta.applyTo(instanceInfoBuilder).build();

        assertThat(instanceInfo.getPorts(), equalTo(newPorts));
        assertThat(instanceInfo.getPorts(), not(equalTo(original.getPorts())));
        assertThat(instanceInfo.getId(), equalTo(original.getId()));
    }

    @Test(timeout = 60000)
    public void testSettingFieldOnInstanceInfo_HashSetString() throws Exception {
        HashSet<String> newHealthCheckUrls = ExtCollections.asSet("http://foo", "http://bar");
        Delta<?> delta = new Delta.Builder()
                .withId(original.getId())
                .withDelta(InstanceInfoField.HEALTHCHECK_URLS, newHealthCheckUrls)
                .build();

        InstanceInfo instanceInfo = delta.applyTo(instanceInfoBuilder).build();

        assertThat(instanceInfo.getHealthCheckUrls(), equalTo(newHealthCheckUrls));
        assertThat(instanceInfo.getHealthCheckUrls(), not(equalTo(original.getHealthCheckUrls())));
        assertThat(instanceInfo.getId(), equalTo(original.getId()));
    }

    @Test(timeout = 60000)
    public void testSettingFieldOnInstanceInfo_String() throws Exception {
        String newHomepage = "http://something.random.net";
        Delta<?> delta = new Delta.Builder()
                .withId(original.getId())
                .withDelta(InstanceInfoField.HOMEPAGE_URL, newHomepage)
                .build();

        InstanceInfo instanceInfo = delta.applyTo(instanceInfoBuilder).build();

        assertThat(instanceInfo.getHomePageUrl(), equalTo(newHomepage));
        assertThat(instanceInfo.getHomePageUrl(), not(equalTo(original.getHomePageUrl())));
        assertThat(instanceInfo.getId(), equalTo(original.getId()));
    }

    @Test(timeout = 60000)
    public void testSettingFieldOnInstanceInfo_InstanceStatus() throws Exception {
        InstanceInfo.Status newStatus = InstanceInfo.Status.OUT_OF_SERVICE;
        Delta<?> delta = new Delta.Builder()
                .withId(original.getId())
                .withDelta(InstanceInfoField.STATUS, newStatus)
                .build();

        InstanceInfo instanceInfo = delta.applyTo(instanceInfoBuilder).build();

        assertThat(instanceInfo.getStatus(), equalTo(newStatus));
        assertThat(instanceInfo.getStatus(), not(equalTo(original.getStatus())));
        assertThat(instanceInfo.getId(), equalTo(original.getId()));
    }

}
