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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import com.netflix.eureka2.model.datacenter.LocalDataCenterInfo;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.spi.codec.EurekaCodec;
import com.netflix.eureka2.spi.codec.EurekaCodecFactory;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import org.junit.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 * Collection of tests verifying capability of the codec to serialize all Eureka's entities that
 * are transferred on the wire.
 */
public abstract class EurekaCodecCompatibilityTest {

    private final EurekaCodecFactory codecFactory;

    protected EurekaCodecCompatibilityTest(EurekaCodecFactory codecFactory) {
        this.codecFactory = codecFactory;
    }

    @Test
    public void testInstanceInfoWithBasicDataCenterInfoEncoding() throws IOException {
        InstanceInfo instance = SampleInstanceInfo.WebServer.builder()
                .withDataCenterInfo(LocalDataCenterInfo.fromSystemData())
                .build();
        EurekaCodec codec = codecFactory.getCodec();

        ByteArrayOutputStream bis = new ByteArrayOutputStream();
        codec.encode(instance, bis);

        InstanceInfo decoded = codec.decode(new ByteArrayInputStream(bis.toByteArray()), InstanceInfo.class);
        assertThat(decoded, is(equalTo(instance)));
    }

    @Test
    public void testInstanceInfoWithAwsDataCenterInfoEncoding() throws IOException {
        InstanceInfo instance = SampleInstanceInfo.WebServer.build();
        EurekaCodec codec = codecFactory.getCodec();

        ByteArrayOutputStream bis = new ByteArrayOutputStream();
        codec.encode(instance, bis);

        InstanceInfo decoded = codec.decode(new ByteArrayInputStream(bis.toByteArray()), InstanceInfo.class);
        assertThat(decoded, is(equalTo(instance)));
    }
}
