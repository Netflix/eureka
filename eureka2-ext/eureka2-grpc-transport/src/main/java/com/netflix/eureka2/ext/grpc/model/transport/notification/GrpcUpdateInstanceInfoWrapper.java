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

import java.util.Set;

import com.netflix.eureka2.ext.grpc.model.GrpcModelConverters;
import com.netflix.eureka2.ext.grpc.model.GrpcObjectWrapper;
import com.netflix.eureka2.ext.grpc.util.TextPrinter;
import com.netflix.eureka2.grpc.Eureka2;
import com.netflix.eureka2.model.instance.Delta;
import com.netflix.eureka2.spi.model.transport.notification.UpdateInstanceInfo;

/**
 */
public class GrpcUpdateInstanceInfoWrapper implements UpdateInstanceInfo, GrpcObjectWrapper<Eureka2.GrpcUpdateInstanceInfo> {

    private final Eureka2.GrpcUpdateInstanceInfo grpcUpdateInstanceInfo;

    private volatile Set<Delta<?>> deltas;

    public GrpcUpdateInstanceInfoWrapper(Eureka2.GrpcUpdateInstanceInfo grpcUpdateInstanceInfo) {
        this.grpcUpdateInstanceInfo = grpcUpdateInstanceInfo;
    }

    @Override
    public Eureka2.GrpcUpdateInstanceInfo getGrpcObject() {
        return grpcUpdateInstanceInfo;
    }

    @Override
    public Set<Delta<?>> getDeltas() {
        if (deltas != null) {
            return deltas;
        }
        return deltas = GrpcModelConverters.toDeltas(grpcUpdateInstanceInfo.getDeltasList());
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof GrpcUpdateInstanceInfoWrapper) {
            return grpcUpdateInstanceInfo.equals(((GrpcUpdateInstanceInfoWrapper) o).getGrpcObject());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return grpcUpdateInstanceInfo.hashCode();
    }

    @Override
    public String toString() {
        return TextPrinter.toString(grpcUpdateInstanceInfo);
    }

    public static UpdateInstanceInfo newUpdateInstanceInfo(Set<Delta<?>> deltas) {
        return new GrpcUpdateInstanceInfoWrapper(
                Eureka2.GrpcUpdateInstanceInfo.newBuilder().addAllDeltas(GrpcModelConverters.toGrpcDeltas(deltas)).build()
        );
    }

    public static UpdateInstanceInfo asUpdateInstanceInfo(Eureka2.GrpcUpdateInstanceInfo grpcUpdateInstanceInfo) {
        return new GrpcUpdateInstanceInfoWrapper(grpcUpdateInstanceInfo);
    }

}
