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

package com.netflix.eureka.server.transport.registration.http;

import com.netflix.karyon.transport.http.KaryonHttpModule;
import io.reactivex.netty.servo.ServoEventsListenerFactory;

/**
 * HTTP server module for discovery registration endpoint.
 *
 * @author Tomasz Bak
 */
public class HttpRegistrationModule extends KaryonHttpModule<Object, Object> {


    public HttpRegistrationModule() {
        super("karyonRegistrationServer", Object.class, Object.class);
    }

    @Override
    protected void configureServer() {
        bindRouter().to(RegistrationHttpRequestRouter.class);
        bindEventsListenerFactory().to(ServoEventsListenerFactory.class);
        server().port(7001).threadPoolSize(100);
    }
}
