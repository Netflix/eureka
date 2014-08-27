package com.netflix.eureka.registry;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import com.netflix.eureka.Sets;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;

import java.util.HashSet;

/**
 * @author David Liu
 */
public class DeltaTest {

    InstanceInfo original;
    InstanceInfo instanceInfo;
    InstanceInfo.Builder instanceInfoBuilder;

    @Rule
    public final ExternalResource testResource = new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            original = SampleInstanceInfo.DiscoveryServer.build();
            instanceInfoBuilder = new InstanceInfo.Builder().withInstanceInfo(original);
            instanceInfo = instanceInfoBuilder.build();
        }

    };

    @Test
    public void testSettingFieldOnInstanceInfo_HashSetInt() throws Exception {
        HashSet<Integer> newPorts = Sets.asSet(111, 222);
        Delta<?> delta = new Delta.Builder()
                .withId(instanceInfo.getId())
                .withVersion(instanceInfo.getVersion() + 1)
                .withDelta(InstanceInfoField.PORTS, newPorts)
                .build();

        delta.applyTo(instanceInfoBuilder);

        assertThat(instanceInfo.getPorts(), equalTo(newPorts));
        assertThat(instanceInfo.getPorts(), not(equalTo(original.getPorts())));
        assertThat(instanceInfo.getId(), equalTo(original.getId()));
    }

    @Test
    public void testSettingFieldOnInstanceInfo_HashSetString() throws Exception {
        HashSet<String> newHealthCheckUrls = Sets.asSet("http://foo", "http://bar");
        Delta<?> delta = new Delta.Builder()
                .withId(instanceInfo.getId())
                .withVersion(instanceInfo.getVersion() + 1)
                .withDelta(InstanceInfoField.HEALTHCHECK_URLS, newHealthCheckUrls)
                .build();

        delta.applyTo(instanceInfoBuilder);

        assertThat(instanceInfo.getHealthCheckUrls(), equalTo(newHealthCheckUrls));
        assertThat(instanceInfo.getHealthCheckUrls(), not(equalTo(original.getHealthCheckUrls())));
        assertThat(instanceInfo.getId(), equalTo(original.getId()));
    }

    @Test
    public void testSettingFieldOnInstanceInfo_String() throws Exception {
        String newHomepage = "http://something.random.net";
        Delta<?> delta = new Delta.Builder()
                .withId(instanceInfo.getId())
                .withVersion(instanceInfo.getVersion() + 1)
                .withDelta(InstanceInfoField.HOMEPAGE_URL, newHomepage)
                .build();

        delta.applyTo(instanceInfoBuilder);

        assertThat(instanceInfo.getHomePageUrl(), equalTo(newHomepage));
        assertThat(instanceInfo.getHomePageUrl(), not(equalTo(original.getHomePageUrl())));
        assertThat(instanceInfo.getId(), equalTo(original.getId()));
    }

    @Test
    public void testSettingFieldOnInstanceInfo_InstanceStatus() throws Exception {
        InstanceInfo.Status newStatus = InstanceInfo.Status.OUT_OF_SERVICE;
        Delta<?> delta = new Delta.Builder()
                .withId(instanceInfo.getId())
                .withVersion(instanceInfo.getVersion() + 1)
                .withDelta(InstanceInfoField.STATUS, newStatus)
                .build();

        delta.applyTo(instanceInfoBuilder);

        assertThat(instanceInfo.getStatus(), equalTo(newStatus));
        assertThat(instanceInfo.getStatus(), not(equalTo(original.getStatus())));
        assertThat(instanceInfo.getId(), equalTo(original.getId()));
    }

}
