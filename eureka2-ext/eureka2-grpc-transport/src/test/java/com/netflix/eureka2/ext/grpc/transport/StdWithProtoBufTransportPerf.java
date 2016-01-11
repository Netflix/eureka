/*
 * Copyright 2016 Netflix, Inc.
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
import com.netflix.eureka2.performance.transport.TransportPerf;
import com.netflix.eureka2.transport.client.StdEurekaClientTransportFactory;
import com.netflix.eureka2.transport.server.StdEurekaServerTransportFactory;

/**
 */
public class StdWithProtoBufTransportPerf extends TransportPerf {
    static {
        GrpcTransportInjector.inject();
    }

    protected StdWithProtoBufTransportPerf(String[] args) {
        super(args, new StdEurekaClientTransportFactory(), new StdEurekaServerTransportFactory());
    }

    public static void main(String[] args) throws InterruptedException {
        new StdWithProtoBufTransportPerf(args).start();
    }
}
