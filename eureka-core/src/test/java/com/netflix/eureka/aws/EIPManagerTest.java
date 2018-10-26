/*
 * Copyright 2018 Netflix, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.netflix.eureka.aws;

import com.google.common.collect.Lists;
import com.netflix.discovery.EurekaClientConfig;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Joseph Witthuhn
 */
public class EIPManagerTest {
    private EurekaClientConfig config = mock(EurekaClientConfig.class);
    private EIPManager eipManager;

    @Before
    public void setUp() {
        when(config.shouldUseDnsForFetchingServiceUrls()).thenReturn(Boolean.FALSE);
        eipManager = new EIPManager(null, config, null, null);
    }

    @Test
    public void shouldFilterNonElasticNames() {
        when(config.getRegion()).thenReturn("us-east-1");
        List<String> hosts = Lists.newArrayList("example.com", "ec2-1-2-3-4.compute.amazonaws.com", "5.6.7.8",
                "ec2-101-202-33-44.compute.amazonaws.com");
        when(config.getEurekaServerServiceUrls(any(String.class))).thenReturn(hosts);

        Collection<String> returnValue = eipManager.getCandidateEIPs("i-123", "us-east-1d");
        assertEquals(2, returnValue.size());
        assertTrue(returnValue.contains("1.2.3.4"));
        assertTrue(returnValue.contains("101.202.33.44"));
    }

    @Test
    public void shouldFilterNonElasticNamesInOtherRegion() {
        when(config.getRegion()).thenReturn("eu-west-1");
        List<String> hosts = Lists.newArrayList("example.com", "ec2-1-2-3-4.eu-west-1.compute.amazonaws.com",
                "5.6.7.8", "ec2-101-202-33-44.eu-west-1.compute.amazonaws.com");
        when(config.getEurekaServerServiceUrls(any(String.class))).thenReturn(hosts);

        Collection<String> returnValue = eipManager.getCandidateEIPs("i-123", "eu-west-1a");
        assertEquals(2, returnValue.size());
        assertTrue(returnValue.contains("1.2.3.4"));
        assertTrue(returnValue.contains("101.202.33.44"));
    }

    @Test(expected = RuntimeException.class)
    public void shouldThrowExceptionWhenNoElasticNames() {
        when(config.getRegion()).thenReturn("eu-west-1");
        List<String> hosts = Lists.newArrayList("example.com", "5.6.7.8");
        when(config.getEurekaServerServiceUrls(any(String.class))).thenReturn(hosts);

        eipManager.getCandidateEIPs("i-123", "eu-west-1a");
    }
}
