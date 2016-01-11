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
import com.netflix.eureka2.ext.grpc.model.interest.GrpcInterestWrapper;
import com.netflix.eureka2.ext.grpc.util.TextPrinter;
import com.netflix.eureka2.grpc.Eureka2;
import com.netflix.eureka2.grpc.Eureka2.GrpcStreamStateUpdate.GrpcBufferState;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.notification.StreamStateNotification;
import com.netflix.eureka2.spi.model.transport.notification.StreamStateUpdate;

/**
 */
public class GrpcStreamStateUpdateWrapper implements StreamStateUpdate, GrpcObjectWrapper<Eureka2.GrpcStreamStateUpdate> {

    private final Eureka2.GrpcStreamStateUpdate grpcStreamStateUpdate;

    private volatile Interest<InstanceInfo> interest;

    public GrpcStreamStateUpdateWrapper(Eureka2.GrpcStreamStateUpdate grpcStreamStateUpdate) {
        this.grpcStreamStateUpdate = grpcStreamStateUpdate;
    }

    @Override
    public Eureka2.GrpcStreamStateUpdate getGrpcObject() {
        return grpcStreamStateUpdate;
    }

    @Override
    public StreamStateNotification.BufferState getState() {
        GrpcBufferState grpcBufferState = grpcStreamStateUpdate.getBufferState();
        if (grpcBufferState == null) {
            return null;
        }
        switch (grpcBufferState) {
            case BufferStart:
                return StreamStateNotification.BufferState.BufferStart;
            case BufferEnd:
                return StreamStateNotification.BufferState.BufferEnd;
            case Unknown:
                return StreamStateNotification.BufferState.Unknown;
        }
        throw new IllegalStateException("Unrecognized buffer state " + grpcBufferState);
    }

    @Override
    public Interest<InstanceInfo> getInterest() {
        if (interest != null) {
            return interest;
        }
        return interest = GrpcInterestWrapper.toInterest(grpcStreamStateUpdate.getInterest());
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof GrpcStreamStateUpdateWrapper) {
            return grpcStreamStateUpdate.equals(((GrpcStreamStateUpdateWrapper) o).getGrpcObject());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return grpcStreamStateUpdate.hashCode();
    }

    @Override
    public String toString() {
        return TextPrinter.toString(grpcStreamStateUpdate);
    }

    public static StreamStateUpdate newStreamStateUpdate(StreamStateNotification.BufferState state, Interest<InstanceInfo> interest) {
        GrpcBufferState grpcBufferState = null;
        if (state != null) {
            switch (state) {
                case BufferStart:
                    grpcBufferState = GrpcBufferState.BufferStart;
                    break;
                case BufferEnd:
                    grpcBufferState = GrpcBufferState.BufferEnd;
                    break;
                case Unknown:
                    grpcBufferState = GrpcBufferState.Unknown;
                    break;
                default:
                    throw new IllegalStateException("Unrecognized buffer state " + state);
            }
        }
        return new GrpcStreamStateUpdateWrapper(
                Eureka2.GrpcStreamStateUpdate.newBuilder()
                        .setBufferState(grpcBufferState)
                        .setInterest(((GrpcInterestWrapper) interest).getGrpcObject())
                        .build()
        );
    }

    public static StreamStateUpdate asStreamStateUpdate(Eureka2.GrpcStreamStateUpdate grpcStreamStateUpdate) {
        return new GrpcStreamStateUpdateWrapper(grpcStreamStateUpdate);
    }
}
