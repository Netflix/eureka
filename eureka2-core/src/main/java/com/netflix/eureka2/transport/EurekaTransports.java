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

package com.netflix.eureka2.transport;

import com.netflix.eureka2.spi.transport.EurekaTransportFactory;
import io.reactivex.netty.pipeline.PipelineConfigurator;

/**
 * Communication endpoint factory methods.
 *
 * @author Tomasz Bak
 */
public final class EurekaTransports {

    private static volatile EurekaTransportFactory defaultTransportFactory;

    private EurekaTransports() {
    }

    public static PipelineConfigurator<Object, Object> registrationPipeline() {
        return defaultTransportFactory.registrationPipeline();
    }

    public static PipelineConfigurator<Object, Object> replicationPipeline() {
        return defaultTransportFactory.replicationPipeline();
    }

    public static PipelineConfigurator<Object, Object> interestPipeline() {
        return defaultTransportFactory.interestPipeline();
    }

    public static EurekaTransportFactory getTransportFactory() {
        return defaultTransportFactory;
    }

    public static EurekaTransportFactory setTransportFactory(EurekaTransportFactory newTransportFactory) {
        EurekaTransportFactory previous = defaultTransportFactory;
        defaultTransportFactory = newTransportFactory;
        return previous;
    }
}