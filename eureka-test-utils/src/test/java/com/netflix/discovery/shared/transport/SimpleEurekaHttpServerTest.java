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

package com.netflix.discovery.shared.transport;

import java.net.URI;

import com.google.common.base.Preconditions;
import com.netflix.appinfo.EurekaAccept;
import com.netflix.discovery.converters.wrappers.CodecWrappers.JacksonJson;
import com.netflix.discovery.shared.resolver.DefaultEndpoint;
import com.netflix.discovery.shared.transport.jersey.JerseyEurekaHttpClientFactory;
import org.junit.After;

/**
 * @author Tomasz Bak
 */
public class SimpleEurekaHttpServerTest extends EurekaHttpClientCompatibilityTestSuite {

    private TransportClientFactory httpClientFactory;
    private EurekaHttpClient eurekaHttpClient;

    @Override
    @After
    public void tearDown() throws Exception {
        httpClientFactory.shutdown();
        super.tearDown();
    }

    @Override
    protected EurekaHttpClient getEurekaHttpClient(URI serviceURI) {
        Preconditions.checkState(eurekaHttpClient == null, "EurekaHttpClient has been already created");

        httpClientFactory = JerseyEurekaHttpClientFactory.newBuilder()
                .withClientName("test")
                .withMaxConnectionsPerHost(10)
                .withMaxTotalConnections(10)
                .withDecoder(JacksonJson.class.getSimpleName(), EurekaAccept.full.name())
                .withEncoder(JacksonJson.class.getSimpleName())
                .build();
        this.eurekaHttpClient = httpClientFactory.newClient(new DefaultEndpoint(serviceURI.toString()));

        return eurekaHttpClient;
    }
}