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
public class ZoneAffinityClusterResolverTest {

    @Test
    public void testApplicationZoneIsFirstOnTheList() throws Exception {
        List<EurekaEndpoint> endpoints = SampleCluster.merge(SampleCluster.UsEast1a, SampleCluster.UsEast1b, SampleCluster.UsEast1c);

        ZoneAffinityClusterResolver resolver = new ZoneAffinityClusterResolver(new StaticClusterResolver("regionA", endpoints), "us-east-1b", true);

        List<EurekaEndpoint> result = resolver.getClusterEndpoints();
        assertThat(result.size(), is(equalTo(endpoints.size())));
        assertThat(result.get(0).getZone(), is(equalTo("us-east-1b")));
    }

    @Test
    public void testAntiAffinity() throws Exception {
        List<EurekaEndpoint> endpoints = SampleCluster.merge(SampleCluster.UsEast1a, SampleCluster.UsEast1b);

        ZoneAffinityClusterResolver resolver = new ZoneAffinityClusterResolver(new StaticClusterResolver("regionA", endpoints), "us-east-1b", false);

        List<EurekaEndpoint> result = resolver.getClusterEndpoints();
        assertThat(result.size(), is(equalTo(endpoints.size())));
        assertThat(result.get(0).getZone(), is(equalTo("us-east-1a")));
    }

    @Test
    public void testUnrecognizedZoneIsIgnored() throws Exception {
        List<EurekaEndpoint> endpoints = SampleCluster.merge(SampleCluster.UsEast1a, SampleCluster.UsEast1b);

        ZoneAffinityClusterResolver resolver = new ZoneAffinityClusterResolver(new StaticClusterResolver("regionA", endpoints), "us-east-1c", true);

        List<EurekaEndpoint> result = resolver.getClusterEndpoints();
        assertThat(result.size(), is(equalTo(endpoints.size())));
    }
}