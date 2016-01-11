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

package com.netflix.eureka2.ext.grpc.transport;

import com.netflix.eureka2.ext.grpc.GrpcTransportInjector;
import com.netflix.eureka2.ext.grpc.transport.client.GrpcEurekaClientTransportFactory;
import com.netflix.eureka2.ext.grpc.transport.server.GrpcEurekaServerTransportFactory;
import com.netflix.eureka2.spi.transport.EurekaClientTransportFactory;
import com.netflix.eureka2.spi.transport.EurekaServerTransportFactory;
import com.netflix.eureka2.testkit.compatibility.transport.EurekaTransportCompatibilityTestSuite;

/**
 */
public class GrpcEurekaTransportCompatibilityTest extends EurekaTransportCompatibilityTestSuite {

    static {
        GrpcTransportInjector.inject();
    }

    @Override
    protected EurekaClientTransportFactory newClientTransportFactory() {
        return new GrpcEurekaClientTransportFactory();
    }

    @Override
    protected EurekaServerTransportFactory newServerTransportFactory() {
        return new GrpcEurekaServerTransportFactory();
    }
}
