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

package com.netflix.eureka2.ext.grpc.model.transport;

import com.netflix.eureka2.ext.grpc.model.GrpcObjectWrapper;
import com.netflix.eureka2.ext.grpc.model.GrpcSourceWrapper;
import com.netflix.eureka2.grpc.Eureka2;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.spi.model.ReplicationServerHello;

/**
 */
public class GrpcReplicationServerHelloWrapper implements GrpcObjectWrapper<Eureka2.GrpcReplicationServerHello>, ReplicationServerHello {
    private final Eureka2.GrpcReplicationServerHello grpcServerHello;

    private volatile Source serverSource;

    private GrpcReplicationServerHelloWrapper(Eureka2.GrpcReplicationServerHello grpcServerHello) {
        this.grpcServerHello = grpcServerHello;
    }

    @Override
    public Source getServerSource() {
        if (serverSource == null) {
            serverSource = GrpcSourceWrapper.asSource(grpcServerHello.getServerSource());
        }
        return serverSource;
    }

    @Override
    public Eureka2.GrpcReplicationServerHello getGrpcObject() {
        return grpcServerHello;
    }

    @Override
    public int hashCode() {
        return grpcServerHello.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof GrpcReplicationServerHelloWrapper) {
            return grpcServerHello.equals(((GrpcReplicationServerHelloWrapper) obj).getGrpcObject());
        }
        return false;
    }

    @Override
    public String toString() {
        return grpcServerHello.toString();
    }

    public static ReplicationServerHello newServerHello(Source serverSource) {
        return new GrpcReplicationServerHelloWrapper(
                Eureka2.GrpcReplicationServerHello.newBuilder()
                        .setServerSource(((GrpcSourceWrapper) serverSource).getGrpcObject())
                        .build()
        );
    }

    public static ReplicationServerHello asServerHello(Eureka2.GrpcReplicationServerHello grpcServerHello) {
        return new GrpcReplicationServerHelloWrapper(grpcServerHello);
    }
}
