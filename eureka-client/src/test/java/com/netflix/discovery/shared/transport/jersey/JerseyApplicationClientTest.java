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

package com.netflix.discovery.shared.transport.jersey;

import com.netflix.discovery.shared.resolver.DefaultEndpoint;
import com.netflix.discovery.shared.transport.EurekaHttpClient;
import com.netflix.discovery.shared.transport.EurekaHttpClientCompatibilityTestSuite;
import com.netflix.discovery.shared.transport.TransportClientFactory;
import org.junit.After;
import org.junit.Before;

public class JerseyApplicationClientTest extends EurekaHttpClientCompatibilityTestSuite {

    private JerseyApplicationClient jerseyHttpClient;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        TransportClientFactory clientFactory = JerseyEurekaHttpClientFactory.newBuilder()
                .withClientName("compatibilityTestClient")
                .withETag(true)
                .build();
        jerseyHttpClient = (JerseyApplicationClient) clientFactory.newClient(new DefaultEndpoint(getHttpServer().getServiceURI().toString()));
    }

    @Override
    @After
    public void tearDown() throws Exception {
        if (jerseyHttpClient != null) {
            jerseyHttpClient.shutdown();
        }
        super.tearDown();
    }

    @Override
    public EurekaHttpClient getEurekaHttpClient() {
        return jerseyHttpClient;
    }
}