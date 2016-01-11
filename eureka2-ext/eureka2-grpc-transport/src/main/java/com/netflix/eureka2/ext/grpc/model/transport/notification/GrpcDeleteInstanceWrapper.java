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

import com.netflix.eureka2.ext.grpc.model.GrpcObjectWrapper;
import com.netflix.eureka2.ext.grpc.util.TextPrinter;
import com.netflix.eureka2.grpc.Eureka2;
import com.netflix.eureka2.spi.model.transport.notification.DeleteInstance;

/**
 */
public class GrpcDeleteInstanceWrapper implements DeleteInstance, GrpcObjectWrapper<Eureka2.GrpcDeleteInstance> {

    private final Eureka2.GrpcDeleteInstance grpcDeleteInstance;

    public GrpcDeleteInstanceWrapper(Eureka2.GrpcDeleteInstance grpcDeleteInstance) {
        this.grpcDeleteInstance = grpcDeleteInstance;
    }

    @Override
    public String getInstanceId() {
        return grpcDeleteInstance.getInstanceId();
    }

    @Override
    public Eureka2.GrpcDeleteInstance getGrpcObject() {
        return grpcDeleteInstance;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof GrpcDeleteInstanceWrapper) {
            return grpcDeleteInstance.equals(((GrpcDeleteInstanceWrapper) o).getGrpcObject());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return grpcDeleteInstance.hashCode();
    }

    @Override
    public String toString() {
        return TextPrinter.toString(grpcDeleteInstance);
    }

    public static DeleteInstance newDeleteInstance(String instanceId) {
        return new GrpcDeleteInstanceWrapper(
                Eureka2.GrpcDeleteInstance.newBuilder().setInstanceId(instanceId).build()
        );
    }

    public static DeleteInstance asDeleteInstance(Eureka2.GrpcDeleteInstance grpcDeleteInstance) {
        return new GrpcDeleteInstanceWrapper(grpcDeleteInstance);
    }
}
