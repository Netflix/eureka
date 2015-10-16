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

import java.net.URL;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Tomasz Bak
 */
public class StaticClusterResolverTest {

    @Test
    public void testClusterResolverFromURL() throws Exception {
        verifyEqual(
                StaticClusterResolver.fromURL("regionA", new URL("http://eureka.test:8080/eureka/v2/apps")),
                new DefaultEndpoint("eureka.test", 8080, false, "/eureka/v2/apps")
        );
        verifyEqual(
                StaticClusterResolver.fromURL("regionA", new URL("https://eureka.test:8081/eureka/v2/apps")),
                new DefaultEndpoint("eureka.test", 8081, true, "/eureka/v2/apps")
        );
        verifyEqual(
                StaticClusterResolver.fromURL("regionA", new URL("http://eureka.test/eureka/v2/apps")),
                new DefaultEndpoint("eureka.test", 80, false, "/eureka/v2/apps")
        );
        verifyEqual(
                StaticClusterResolver.fromURL("regionA", new URL("https://eureka.test/eureka/v2/apps")),
                new DefaultEndpoint("eureka.test", 443, true, "/eureka/v2/apps")
        );
    }

    private static void verifyEqual(ClusterResolver<EurekaEndpoint> actual, EurekaEndpoint expected) {
        assertThat(actual.getClusterEndpoints().get(0), is(equalTo(expected)));
    }
}