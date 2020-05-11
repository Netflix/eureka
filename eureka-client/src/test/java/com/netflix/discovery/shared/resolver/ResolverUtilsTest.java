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

package com.netflix.discovery.shared.resolver;

import java.util.List;

import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.DataCenterInfo;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.MyDataCenterInfo;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.discovery.shared.resolver.aws.AwsEndpoint;
import com.netflix.discovery.shared.resolver.aws.SampleCluster;
import com.netflix.discovery.shared.transport.EurekaTransportConfig;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Tomasz Bak
 */
public class ResolverUtilsTest {

    @Test
    public void testSplitByZone() throws Exception {
        List<AwsEndpoint> endpoints = SampleCluster.merge(SampleCluster.UsEast1a, SampleCluster.UsEast1b, SampleCluster.UsEast1c);
        List<AwsEndpoint>[] parts = ResolverUtils.splitByZone(endpoints, "us-east-1b");

        List<AwsEndpoint> myZoneServers = parts[0];
        List<AwsEndpoint> remainingServers = parts[1];

        assertThat(myZoneServers, is(equalTo(SampleCluster.UsEast1b.build())));
        assertThat(remainingServers, is(equalTo(SampleCluster.merge(SampleCluster.UsEast1a, SampleCluster.UsEast1c))));
    }

    @Test
    public void testExtractZoneFromHostName() throws Exception {
        assertThat(ResolverUtils.extractZoneFromHostName("us-east-1c.myservice.net"), is(equalTo("us-east-1c")));
        assertThat(ResolverUtils.extractZoneFromHostName("txt.us-east-1c.myservice.net"), is(equalTo("us-east-1c")));
    }

    @Test
    public void testIdentical() throws Exception {
        List<AwsEndpoint> firstList = SampleCluster.UsEast1a.builder().withServerPool(10).build();
        List<AwsEndpoint> secondList = ResolverUtils.randomize(firstList);

        assertThat(ResolverUtils.identical(firstList, secondList), is(true));

        secondList.set(0, SampleCluster.UsEast1b.build().get(0));
        assertThat(ResolverUtils.identical(firstList, secondList), is(false));
    }

    @Test
    public void testInstanceInfoToEndpoint() throws Exception {
        EurekaClientConfig clientConfig = mock(EurekaClientConfig.class);
        when(clientConfig.getEurekaServerURLContext()).thenReturn("/eureka");
        when(clientConfig.getRegion()).thenReturn("region");

        EurekaTransportConfig transportConfig = mock(EurekaTransportConfig.class);
        when(transportConfig.applicationsResolverUseIp()).thenReturn(false);

        AmazonInfo amazonInfo = AmazonInfo.Builder.newBuilder().addMetadata(AmazonInfo.MetaDataKey.availabilityZone,
                "us-east-1c").build();
        InstanceInfo instanceWithAWSInfo = InstanceInfo.Builder.newBuilder().setAppName("appName")
                .setHostName("hostName").setPort(8080).setDataCenterInfo(amazonInfo).build();
        AwsEndpoint awsEndpoint = ResolverUtils.instanceInfoToEndpoint(clientConfig, transportConfig, instanceWithAWSInfo);
        assertEquals("zone not equals.", "us-east-1c", awsEndpoint.getZone());

        MyDataCenterInfo myDataCenterInfo = new MyDataCenterInfo(DataCenterInfo.Name.MyOwn);
        InstanceInfo instanceWithMyDataInfo = InstanceInfo.Builder.newBuilder().setAppName("appName")
                .setHostName("hostName").setPort(8080).setDataCenterInfo(myDataCenterInfo)
                .add("zone", "us-east-1c").build();
        awsEndpoint = ResolverUtils.instanceInfoToEndpoint(clientConfig, transportConfig, instanceWithMyDataInfo);
        assertEquals("zone not equals.", "us-east-1c", awsEndpoint.getZone());
    }
}