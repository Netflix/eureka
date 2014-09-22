/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.eureka.server.transport.tcp.discovery;

import com.google.inject.Inject;
import com.netflix.eureka.transport.EurekaTransports;
import com.netflix.karyon.transport.tcp.KaryonTcpModule;

/**
 * @author Tomasz Bak
 */
public class TcpDiscoveryModule extends KaryonTcpModule<Object, Object> {

    private final int port;

    @Inject
    public TcpDiscoveryModule() {
        this("avroDiscoveryServer", 7003);
    }

    public TcpDiscoveryModule(String moduleName, int port) {
        super(moduleName, Object.class, Object.class);
        this.port = port;
    }

    @Override
    protected void configureServer() {
        bindPipelineConfigurator().toInstance(EurekaTransports.discoveryPipeline(EurekaTransports.Codec.Json));
        bindConnectionHandler().to(TcpDiscoveryHandler.class);
        server().port(port);
    }
}
