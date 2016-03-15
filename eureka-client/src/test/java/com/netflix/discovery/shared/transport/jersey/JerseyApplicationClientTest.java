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

import java.net.URI;

import com.google.common.base.Preconditions;
import com.netflix.discovery.shared.resolver.DefaultEndpoint;
import com.netflix.discovery.shared.transport.EurekaHttpClient;
import com.netflix.discovery.shared.transport.EurekaHttpClientCompatibilityTestSuite;
import com.netflix.discovery.shared.transport.TransportClientFactory;
import org.junit.After;

public class JerseyApplicationClientTest extends EurekaHttpClientCompatibilityTestSuite {

    private JerseyApplicationClient jerseyHttpClient;

    @Override
    @After
    public void tearDown() throws Exception {
        if (jerseyHttpClient != null) {
            jerseyHttpClient.shutdown();
        }
        super.tearDown();
    }

    @Override
    protected EurekaHttpClient getEurekaHttpClient(URI serviceURI) {
        Preconditions.checkState(jerseyHttpClient == null, "EurekaHttpClient has been already created");

        TransportClientFactory clientFactory = JerseyEurekaHttpClientFactory.newBuilder()
                .withClientName("compatibilityTestClient")
                .build();
        jerseyHttpClient = (JerseyApplicationClient) clientFactory.newClient(new DefaultEndpoint(serviceURI.toString()));

        return jerseyHttpClient;
    }
}