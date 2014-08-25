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

import com.google.inject.binder.AnnotatedBindingBuilder;
import com.netflix.karyon.transport.http.AbstractHttpModule;
import com.netflix.karyon.transport.http.HttpRequestRouter;

/**
 * @author Tomasz Bak
 */
public class HttpRegistrationModule{}
//public class HttpRegistrationModule extends AbstractHttpModule<Object, Object> {
//    public HttpRegistrationModule() {
//        super(Object.class, Object.class);
//    }
//
//    @Override
//    public int serverPort() {
//        return 8851;
//    }
//
//    @Override
//    protected void bindRequestRouter(AnnotatedBindingBuilder<HttpRequestRouter<Object, Object>> bind) {
//        bind.to(RegistrationHttpRequestRouter.class);
//    }
//}
