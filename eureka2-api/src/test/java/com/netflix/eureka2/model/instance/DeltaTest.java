/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.eureka2.model.instance;

import java.util.HashSet;
import java.util.Set;

import com.netflix.eureka2.model.InstanceModel;
import com.netflix.eureka2.model.instance.InstanceInfo.Status;
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
    InstanceInfoBuilder instanceInfoBuilder;

    @Rule
    public final ExternalResource testResource = new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            original = SampleInstanceInfo.DiscoveryServer.build();
            instanceInfoBuilder = InstanceModel.getDefaultModel().newInstanceInfo().withInstanceInfo(original);
        }

    };

    @Test
    public void testSettingFieldOnInstanceInfo_HashSetInt() throws Exception {
        Set<ServicePort> newPorts = SampleServicePort.httpPorts();
        Delta<?> delta = InstanceModel.getDefaultModel().newDelta()
                .withId(original.getId())
                .withDelta(InstanceInfoField.PORTS, (HashSet<ServicePort>) newPorts)
                .build();

        InstanceInfo instanceInfo = delta.applyTo(instanceInfoBuilder).build();

        assertThat(instanceInfo.getPorts(), equalTo(newPorts));
        assertThat(instanceInfo.getPorts(), not(equalTo(original.getPorts())));
        assertThat(instanceInfo.getId(), equalTo(original.getId()));
    }

    @Test
    public void testSettingFieldOnInstanceInfo_HashSetString() throws Exception {
        HashSet<String> newHealthCheckUrls = ExtCollections.asSet("http://foo", "http://bar");
        Delta<?> delta = new StdDelta.Builder()
                .withId(original.getId())
                .withDelta(InstanceInfoField.HEALTHCHECK_URLS, newHealthCheckUrls)
                .build();

        InstanceInfo instanceInfo = delta.applyTo(instanceInfoBuilder).build();

        assertThat(instanceInfo.getHealthCheckUrls(), equalTo(newHealthCheckUrls));
        assertThat(instanceInfo.getHealthCheckUrls(), not(equalTo(original.getHealthCheckUrls())));
        assertThat(instanceInfo.getId(), equalTo(original.getId()));
    }

    @Test
    public void testSettingFieldOnInstanceInfo_String() throws Exception {
        String newHomepage = "http://something.random.net";
        Delta<?> delta = new StdDelta.Builder()
                .withId(original.getId())
                .withDelta(InstanceInfoField.HOMEPAGE_URL, newHomepage)
                .build();

        InstanceInfo instanceInfo = delta.applyTo(instanceInfoBuilder).build();

        assertThat(instanceInfo.getHomePageUrl(), equalTo(newHomepage));
        assertThat(instanceInfo.getHomePageUrl(), not(equalTo(original.getHomePageUrl())));
        assertThat(instanceInfo.getId(), equalTo(original.getId()));
    }

    @Test
    public void testSettingFieldOnInstanceInfo_InstanceStatus() throws Exception {
        Status newStatus = InstanceInfo.Status.OUT_OF_SERVICE;
        Delta<?> delta = new StdDelta.Builder()
                .withId(original.getId())
                .withDelta(InstanceInfoField.STATUS, newStatus)
                .build();

        InstanceInfo instanceInfo = delta.applyTo(instanceInfoBuilder).build();

        assertThat(instanceInfo.getStatus(), equalTo(newStatus));
        assertThat(instanceInfo.getStatus(), not(equalTo(original.getStatus())));
        assertThat(instanceInfo.getId(), equalTo(original.getId()));
    }
}
