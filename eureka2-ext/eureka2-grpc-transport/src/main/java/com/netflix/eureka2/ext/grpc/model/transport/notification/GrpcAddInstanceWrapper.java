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

package com.netflix.eureka2.ext.grpc.model.transport.notification;

import com.netflix.eureka2.ext.grpc.model.GrpcModelConverters;
import com.netflix.eureka2.ext.grpc.model.GrpcObjectWrapper;
import com.netflix.eureka2.ext.grpc.model.instance.GrpcInstanceInfoWrapper;
import com.netflix.eureka2.ext.grpc.util.TextPrinter;
import com.netflix.eureka2.grpc.Eureka2;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.spi.model.transport.notification.AddInstance;

/**
 */
public class GrpcAddInstanceWrapper implements AddInstance, GrpcObjectWrapper<Eureka2.GrpcAddInstance> {

    private final Eureka2.GrpcAddInstance grpcAddInstance;

    private volatile InstanceInfo instanceInfo;

    public GrpcAddInstanceWrapper(Eureka2.GrpcAddInstance grpcAddInstance) {
        this.grpcAddInstance = grpcAddInstance;
    }

    @Override
    public InstanceInfo getInstanceInfo() {
        if (instanceInfo != null) {
            return instanceInfo;
        }
        return instanceInfo = GrpcModelConverters.toInstanceInfo(grpcAddInstance.getInstanceInfo());
    }

    @Override
    public Eureka2.GrpcAddInstance getGrpcObject() {
        return grpcAddInstance;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof GrpcAddInstanceWrapper) {
            return grpcAddInstance.equals(((GrpcAddInstanceWrapper) o).getGrpcObject());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return grpcAddInstance.hashCode();
    }

    @Override
    public String toString() {
        return TextPrinter.toString(grpcAddInstance);
    }

    public static AddInstance newAddInstance(InstanceInfo instanceInfo) {
        return new GrpcAddInstanceWrapper(
                Eureka2.GrpcAddInstance.newBuilder().setInstanceInfo(((GrpcInstanceInfoWrapper) instanceInfo).getGrpcObject()).build()
        );
    }

    public static AddInstance asAddInstance(Eureka2.GrpcAddInstance grpcAddInstance) {
        return new GrpcAddInstanceWrapper(grpcAddInstance);
    }
}
