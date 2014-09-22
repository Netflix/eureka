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

package com.netflix.eureka.server.transport.tcp.registration;

import javax.inject.Inject;

import com.netflix.eureka.transport.EurekaTransports;
import com.netflix.eureka.transport.EurekaTransports.Codec;
import com.netflix.karyon.transport.tcp.KaryonTcpModule;

/**
 * @author Tomasz Bak
 */
public class JsonRegistrationModule extends KaryonTcpModule<Object, Object> {

    private final int port;

    @Inject
    public JsonRegistrationModule() {
        this("jsonRegistrationServer", 7002);
    }

    public JsonRegistrationModule(String moduleName, int port) {
        super(moduleName, Object.class, Object.class);
        this.port = port;
    }

    @Override
    protected void configureServer() {
        bindPipelineConfigurator().toInstance(EurekaTransports.registrationPipeline(Codec.Json));
        bindConnectionHandler().to(TcpRegistrationHandler.class);
        server().port(port);
    }
}
