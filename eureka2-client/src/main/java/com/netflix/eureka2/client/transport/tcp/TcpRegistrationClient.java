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

package com.netflix.eureka2.client.transport.tcp;

import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.client.transport.ResolverBasedTransportClient;
import com.netflix.eureka2.config.EurekaTransportConfig;
import com.netflix.eureka2.metric.MessageConnectionMetrics;
import com.netflix.eureka2.transport.EurekaTransports;
import com.netflix.eureka2.transport.TransportClient;

/**
 * A {@link TransportClient} implementation for TCP based connections.
 *
 * @author Tomasz Bak
 */
public class TcpRegistrationClient extends ResolverBasedTransportClient {
    public TcpRegistrationClient(EurekaTransportConfig config, ServerResolver resolver, MessageConnectionMetrics metrics) {
        super(config, resolver, EurekaTransports.registrationPipeline(config.getCodec()), metrics);
    }
}
