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

package com.netflix.eureka2.ext.grpc.transport.client;

import com.netflix.eureka2.grpc.Eureka2InterestGrpc;
import com.netflix.eureka2.grpc.Eureka2RegistrationGrpc;
import com.netflix.eureka2.model.Server;
import com.netflix.eureka2.spi.channel.InterestHandler;
import com.netflix.eureka2.spi.channel.RegistrationHandler;
import com.netflix.eureka2.spi.channel.ReplicationHandler;
import com.netflix.eureka2.spi.transport.EurekaClientTransportFactory;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

/**
 * For each client connection a new stub is created, as the stub holds information about a particular
 * client session.
 */
public class GrpcEurekaClientTransportFactory implements EurekaClientTransportFactory {

    public GrpcEurekaClientTransportFactory(String clientId) {
    }

    @Override
    public RegistrationHandler newRegistrationClientTransport(Server eurekaServer) {
        ManagedChannel channel = createManagedChannel(eurekaServer);
        Eureka2RegistrationGrpc.Eureka2RegistrationStub stub = Eureka2RegistrationGrpc.newStub(channel);

        return new GrpcRegistrationClientTransportHandler(stub);
    }

    @Override
    public InterestHandler newInterestTransport(Server eurekaServer) {
        ManagedChannel channel = createManagedChannel(eurekaServer);
        Eureka2InterestGrpc.Eureka2InterestStub stub = Eureka2InterestGrpc.newStub(channel);

        return new GrpcInterestClientTransportHandler(stub);
    }

    @Override
    public ReplicationHandler newReplicationTransport(Server eurekaServer) {
        throw new IllegalStateException("Not implemented");
    }

    private static ManagedChannel createManagedChannel(Server eurekaServer) {
        return ManagedChannelBuilder.forAddress(eurekaServer.getHost(), eurekaServer.getPort())
                .usePlaintext(true)
                .build();
    }
}
