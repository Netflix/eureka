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

import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Tomasz Bak
 */
public class ResolverUtilsTest {

    @Test
    public void testSplitByZone() throws Exception {
        List<EurekaEndpoint> endpoints = SampleCluster.merge(SampleCluster.UsEast1a, SampleCluster.UsEast1b, SampleCluster.UsEast1c);
        List<EurekaEndpoint>[] parts = ResolverUtils.splitByZone(endpoints, "us-east-1b");

        List<EurekaEndpoint> myZoneServers = parts[0];
        List<EurekaEndpoint> remainingServers = parts[1];

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
        List<EurekaEndpoint> firstList = SampleCluster.UsEast1a.builder().withServerPool(10).build();
        List<EurekaEndpoint> secondList = ResolverUtils.randomize(firstList);

        StaticClusterResolver firstResolver = new StaticClusterResolver(firstList);
        StaticClusterResolver secondResolver = new StaticClusterResolver(secondList);

        assertThat(ResolverUtils.identical(firstResolver, secondResolver), is(true));

        secondList.set(0, SampleCluster.UsEast1b.build().get(0));
        StaticClusterResolver differentResolver = new StaticClusterResolver(secondList);
        assertThat(ResolverUtils.identical(firstResolver, differentResolver), is(false));
    }
}