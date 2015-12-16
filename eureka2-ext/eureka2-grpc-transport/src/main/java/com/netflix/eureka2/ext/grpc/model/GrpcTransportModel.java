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

package com.netflix.eureka2.ext.grpc.model;

import com.netflix.eureka2.ext.grpc.model.transport.GrpcClientHelloWrapper;
import com.netflix.eureka2.ext.grpc.model.transport.GrpcReplicationClientHelloWrapper;
import com.netflix.eureka2.ext.grpc.model.transport.GrpcServerHelloWrapper;
import com.netflix.eureka2.ext.grpc.model.transport.HeartbeatImpl;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.spi.model.*;

/**
 */
public class GrpcTransportModel extends TransportModel {
    private static final TransportModel INSTANCE = new GrpcTransportModel();

    @Override
    public Heartbeat creatHeartbeat() {
        return HeartbeatImpl.getInstance();
    }

    @Override
    public ClientHello newClientHello(Source clientSource) {
        return GrpcClientHelloWrapper.newClientHello(clientSource);
    }

    @Override
    public ReplicationClientHello newReplicationClientHello(Source clientSource, int registrySize) {
        return GrpcReplicationClientHelloWrapper.newClientHello(clientSource, registrySize);
    }

    @Override
    public ServerHello newServerHello(Source serverSource) {
        return GrpcServerHelloWrapper.newServerHello(serverSource);
    }

    public static TransportModel getGrpcModel() {
        return INSTANCE;
    }
}
