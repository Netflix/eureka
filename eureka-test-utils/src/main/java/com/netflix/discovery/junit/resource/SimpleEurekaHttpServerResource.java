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

package com.netflix.discovery.junit.resource;

import com.netflix.discovery.shared.transport.EurekaHttpClient;
import com.netflix.discovery.shared.transport.SimpleEurekaHttpServer;
import org.junit.rules.ExternalResource;

import static org.mockito.Mockito.mock;

/**
 * @author Tomasz Bak
 */
public class SimpleEurekaHttpServerResource extends ExternalResource {

    private final EurekaHttpClient requestHandler = mock(EurekaHttpClient.class);

    private SimpleEurekaHttpServer eurekaHttpServer;

    @Override
    protected void before() throws Throwable {
        eurekaHttpServer = new SimpleEurekaHttpServer(requestHandler);
    }

    @Override
    protected void after() {
        if (eurekaHttpServer != null) {
            eurekaHttpServer.shutdown();
        }
    }

    public EurekaHttpClient getRequestHandler() {
        return requestHandler;
    }

    public SimpleEurekaHttpServer getEurekaHttpServer() {
        return eurekaHttpServer;
    }
}
