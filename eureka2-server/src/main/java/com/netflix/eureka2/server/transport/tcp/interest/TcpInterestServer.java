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

package com.netflix.eureka2.server.transport.tcp.interest;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import com.netflix.eureka2.Names;
import com.netflix.eureka2.server.config.EurekaServerTransportConfig;
import com.netflix.eureka2.server.transport.tcp.AbstractTcpServer;
import com.netflix.eureka2.transport.EurekaTransports;
import io.reactivex.netty.metrics.MetricEventsListenerFactory;

/**
 * @author Tomasz Bak
 */
@Singleton
public class TcpInterestServer extends AbstractTcpServer {

    @Inject
    public TcpInterestServer(EurekaServerTransportConfig config,
                             @Named(Names.INTEREST) MetricEventsListenerFactory servoEventsListenerFactory,
                             TcpInterestHandler tcpDiscoveryHandler) {
        super(servoEventsListenerFactory, config, config.getInterestPort(),
                EurekaTransports.interestPipeline(), tcpDiscoveryHandler);
    }
}
