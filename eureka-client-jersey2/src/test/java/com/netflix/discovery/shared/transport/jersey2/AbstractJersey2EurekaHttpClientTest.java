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

package com.netflix.discovery.shared.transport.jersey2;

import java.net.URI;

import com.netflix.discovery.shared.resolver.DefaultEndpoint;
import com.netflix.discovery.shared.transport.EurekaHttpClient;
import com.netflix.discovery.shared.transport.EurekaHttpClientCompatibilityTestSuite;
import com.netflix.discovery.shared.transport.TransportClientFactory;
import com.netflix.discovery.shared.transport.jersey2.Jersey2ApplicationClientFactory.Jersey2ApplicationClientFactoryBuilder;
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.junit.After;

/**
 * @author Tomasz Bak
 */
public class AbstractJersey2EurekaHttpClientTest extends EurekaHttpClientCompatibilityTestSuite {

    private AbstractJersey2EurekaHttpClient jersey2HttpClient;

    @Override
    @After
    public void tearDown() throws Exception {
        if (jersey2HttpClient != null) {
            jersey2HttpClient.shutdown();
        }
        super.tearDown();
    }

    @Override
    protected EurekaHttpClient getEurekaHttpClient(URI serviceURI) {
        Jersey2ApplicationClientFactoryBuilder factoryBuilder = Jersey2ApplicationClientFactory.newBuilder();
        if (serviceURI.getUserInfo() != null) {
            factoryBuilder.withFeature(HttpAuthenticationFeature.basicBuilder().build());
        }
        TransportClientFactory clientFactory = factoryBuilder.build();
        jersey2HttpClient = (AbstractJersey2EurekaHttpClient) clientFactory.newClient(new DefaultEndpoint(serviceURI.toString()));
        return jersey2HttpClient;
    }
}