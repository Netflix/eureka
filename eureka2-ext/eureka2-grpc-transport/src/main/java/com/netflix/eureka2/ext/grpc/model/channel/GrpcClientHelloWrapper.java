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

package com.netflix.eureka2.ext.grpc.model.channel;

import com.netflix.eureka2.ext.grpc.model.GrpcObjectWrapper;
import com.netflix.eureka2.ext.grpc.model.GrpcSourceWrapper;
import com.netflix.eureka2.grpc.Eureka2;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.spi.model.channel.ClientHello;

/**
 */
public class GrpcClientHelloWrapper implements GrpcObjectWrapper<Eureka2.GrpcClientHello>, ClientHello {

    private final Eureka2.GrpcClientHello grpcClientHello;

    private volatile Source clientSource;

    private GrpcClientHelloWrapper(Eureka2.GrpcClientHello grpcClientHello) {
        this.grpcClientHello = grpcClientHello;
    }

    @Override
    public Source getClientSource() {
        if (clientSource == null) {
            clientSource = GrpcSourceWrapper.asSource(grpcClientHello.getClientSource());
        }
        return clientSource;
    }

    @Override
    public Eureka2.GrpcClientHello getGrpcObject() {
        return grpcClientHello;
    }

    @Override
    public int hashCode() {
        return grpcClientHello.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof GrpcClientHelloWrapper)) {
            return false;
        }
        return grpcClientHello.equals(((GrpcClientHelloWrapper) obj).getGrpcObject());
    }

    @Override
    public String toString() {
        return grpcClientHello.toString();
    }

    public static ClientHello newClientHello(Source clientSource) {
        return new GrpcClientHelloWrapper(
                Eureka2.GrpcClientHello.newBuilder()
                        .setClientSource(((GrpcSourceWrapper) clientSource).getGrpcObject())
                        .build()
        );
    }

    public static ClientHello asClientHello(Eureka2.GrpcClientHello grpcClientHello) {
        return new GrpcClientHelloWrapper(grpcClientHello);
    }
}
