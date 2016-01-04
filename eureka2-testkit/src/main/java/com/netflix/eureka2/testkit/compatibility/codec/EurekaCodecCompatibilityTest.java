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

package com.netflix.eureka2.testkit.compatibility.codec;

import com.netflix.eureka2.model.InstanceModel;
import com.netflix.eureka2.model.datacenter.LocalDataCenterInfo;
import com.netflix.eureka2.model.instance.Delta;
import com.netflix.eureka2.model.instance.DeltaBuilder;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.instance.InstanceInfoField;
import com.netflix.eureka2.spi.codec.EurekaCodec;
import com.netflix.eureka2.spi.codec.EurekaCodecFactory;
import com.netflix.eureka2.testkit.data.builder.SampleAwsDataCenterInfo;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.testkit.data.builder.SampleServicePort;
import com.netflix.eureka2.utils.ExtCollections;
import com.sun.org.apache.xpath.internal.operations.String;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 * Collection of tests verifying capability of the codec to serialize all Eureka's entities that
 * are transferred on the wire.
 */
public abstract class EurekaCodecCompatibilityTest {

    private final EurekaCodec codec;

    protected EurekaCodecCompatibilityTest(EurekaCodecFactory codecFactory) {
        this.codec = codecFactory.getCodec();
    }

    @Test
    public void testInstanceInfoWithBasicDataCenterInfoEncoding() throws IOException {
        InstanceInfo instance = SampleInstanceInfo.WebServer.builder()
                .withDataCenterInfo(LocalDataCenterInfo.fromSystemData())
                .build();
        InstanceInfo decoded = encodeDecode(instance);
        assertThat(decoded, is(equalTo(instance)));
    }

    @Test
    public void testInstanceInfoWithAwsDataCenterInfoEncoding() throws IOException {
        InstanceInfo instance = SampleInstanceInfo.WebServer.build();
        InstanceInfo decoded = encodeDecode(instance);
        assertThat(decoded, is(equalTo(instance)));
    }

    @Test
    public void testDeltaEncoding() throws IOException {
        DeltaBuilder builder = InstanceModel.getDefaultModel().newDelta().withId("id1");

        verifyDelta(builder.withDelta(InstanceInfoField.APPLICATION, "newApp"));
        verifyDelta(builder.withDelta(InstanceInfoField.APPLICATION_GROUP, "newAppGroup"));
        verifyDelta(builder.withDelta(InstanceInfoField.ASG, "newAsg"));
        verifyDelta(builder.withDelta(InstanceInfoField.DATA_CENTER_INFO, SampleAwsDataCenterInfo.UsEast1a.build()));
        verifyDelta(builder.withDelta(InstanceInfoField.HEALTHCHECK_URLS, ExtCollections.asSet("http://newHealthCheck1", "http://newHealthCheck2")));
        verifyDelta(builder.withDelta(InstanceInfoField.HOMEPAGE_URL, "http://homepage"));

        Map<java.lang.String, java.lang.String> metaData = new HashMap<>();
        metaData.put("key1", "value1");
        verifyDelta(builder.withDelta(InstanceInfoField.META_DATA, metaData));

        verifyDelta(builder.withDelta(InstanceInfoField.PORTS, SampleServicePort.httpPorts()));
        verifyDelta(builder.withDelta(InstanceInfoField.SECURE_VIP_ADDRESS, "secureVip"));
        verifyDelta(builder.withDelta(InstanceInfoField.STATUS, InstanceInfo.Status.DOWN));
        verifyDelta(builder.withDelta(InstanceInfoField.STATUS_PAGE_URL, "http://statuspage"));
        verifyDelta(builder.withDelta(InstanceInfoField.VIP_ADDRESS, "unsecureVip"));
    }

    private void verifyDelta(DeltaBuilder builder) throws IOException {
        Delta<?> delta = builder.build();
        Delta<?> decoded = encodeDecode(delta);
        assertThat(decoded, is(equalTo(delta)));
    }

    private <T> T encodeDecode(T object) throws IOException {
        ByteArrayOutputStream bis = new ByteArrayOutputStream();
        codec.encode(object, bis);
        return codec.decode(new ByteArrayInputStream(bis.toByteArray()), (Class<T>) object.getClass());
    }
}
