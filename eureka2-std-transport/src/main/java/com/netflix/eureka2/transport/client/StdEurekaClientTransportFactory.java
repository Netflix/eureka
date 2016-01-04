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

package com.netflix.eureka2.transport.client;

import com.netflix.eureka2.model.Server;
import com.netflix.eureka2.spi.channel.InterestHandler;
import com.netflix.eureka2.spi.channel.RegistrationHandler;
import com.netflix.eureka2.spi.channel.ReplicationHandler;
import com.netflix.eureka2.spi.transport.EurekaClientTransportFactory;

/**
 */
public class StdEurekaClientTransportFactory extends EurekaClientTransportFactory {
    @Override
    public RegistrationHandler newRegistrationClientTransport(Server eurekaServer) {
        return new StdRegistrationClientTransportHandler(eurekaServer);
    }

    @Override
    public InterestHandler newInterestTransport(Server eurekaServer) {
        return new StdInterestClientTransportHandler(eurekaServer);
    }

    @Override
    public ReplicationHandler newReplicationTransport(Server eurekaServer) {
        return new StdReplicationClientTransportHandler(eurekaServer);
    }
}
