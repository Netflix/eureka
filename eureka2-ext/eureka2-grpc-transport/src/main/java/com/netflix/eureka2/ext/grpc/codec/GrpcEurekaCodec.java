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

package com.netflix.eureka2.ext.grpc.codec;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.netflix.eureka2.ext.grpc.model.channel.*;
import com.netflix.eureka2.ext.grpc.model.instance.GrpcDeltaWrapper;
import com.netflix.eureka2.ext.grpc.model.instance.GrpcInstanceInfoWrapper;
import com.netflix.eureka2.ext.grpc.model.transport.GrpcAcknowledgementWrapper;
import com.netflix.eureka2.ext.grpc.model.transport.GrpcGoAwayWrapper;
import com.netflix.eureka2.ext.grpc.model.transport.GrpcInterestRegistrationWrapper;
import com.netflix.eureka2.ext.grpc.model.transport.GrpcProtocolMessageEnvelopeWrapper;
import com.netflix.eureka2.ext.grpc.model.transport.notification.GrpcAddInstanceWrapper;
import com.netflix.eureka2.ext.grpc.model.transport.notification.GrpcDeleteInstanceWrapper;
import com.netflix.eureka2.ext.grpc.model.transport.notification.GrpcStreamStateUpdateWrapper;
import com.netflix.eureka2.ext.grpc.model.transport.notification.GrpcUpdateInstanceInfoWrapper;
import com.netflix.eureka2.grpc.Eureka2;
import com.netflix.eureka2.model.instance.Delta;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.spi.codec.EurekaCodec;
import com.netflix.eureka2.spi.model.transport.ProtocolMessageEnvelope;

/**
 */
public class GrpcEurekaCodec extends EurekaCodec {

    static final Class<?>[] REGISTRATION_PROTOCOL_MODEL = {
            GrpcInstanceInfoWrapper.class, GrpcHeartbeatWrapper.class, GrpcAcknowledgementWrapper.class,
            GrpcGoAwayWrapper.class, GrpcClientHelloWrapper.class, GrpcServerHelloWrapper.class
    };

    static final Class<?>[] REPLICATION_PROTOCOL_MODEL = {
            GrpcHeartbeatWrapper.class, GrpcReplicationClientHelloWrapper.class, GrpcReplicationServerHelloWrapper.class,
            GrpcAddInstanceWrapper.class, GrpcDeleteInstanceWrapper.class, GrpcStreamStateUpdateWrapper.class,
            GrpcAcknowledgementWrapper.class
    };

    static final Class<?>[] INTEREST_PROTOCOL_MODEL = {
            GrpcInterestRegistrationWrapper.class, GrpcHeartbeatWrapper.class,
            GrpcAddInstanceWrapper.class, GrpcDeleteInstanceWrapper.class, GrpcUpdateInstanceInfoWrapper.class, GrpcStreamStateUpdateWrapper.class,
            GrpcAcknowledgementWrapper.class
    };

    static final Set<Class<?>> SUPPORTED_TYPES = new HashSet<>(combined(REGISTRATION_PROTOCOL_MODEL, REPLICATION_PROTOCOL_MODEL, INTEREST_PROTOCOL_MODEL));

    private final Set<Class<?>> acceptTypes;

    public GrpcEurekaCodec(Set<Class<?>> acceptTypes) {
        if (!SUPPORTED_TYPES.containsAll(acceptTypes)) {
            HashSet<Class<?>> unsupported = new HashSet<>(acceptTypes);
            unsupported.removeAll(SUPPORTED_TYPES);
            throw new IllegalArgumentException("Unsupported types " + unsupported);
        }
        this.acceptTypes = acceptTypes;
    }

    @Override
    public boolean accept(Class<?> valueType) {
        return acceptTypes.contains(valueType);
    }

    @Override
    public <T> void encode(T value, OutputStream output) throws IOException {
        if (value instanceof GrpcInstanceInfoWrapper) {
            Eureka2.GrpcInstanceInfo grpcInstanceInfo = ((GrpcInstanceInfoWrapper) value).getGrpcObject();
            grpcInstanceInfo.writeTo(output);
        } else if (value instanceof GrpcDeltaWrapper) {
            Eureka2.GrpcDelta grpcDelta = ((GrpcDeltaWrapper) value).getGrpcObject();
            grpcDelta.writeTo(output);
        } else if (value instanceof GrpcProtocolMessageEnvelopeWrapper) {
            Eureka2.GrpcProtocolMessageEnvelope grpcEnvelope = ((GrpcProtocolMessageEnvelopeWrapper) value).getGrpcObject();
            grpcEnvelope.writeTo(output);
        } else {
            throw new IllegalArgumentException("Grpc codec does not support type " + value.getClass());
        }
    }

    @Override
    public <T> T decode(InputStream source, Class<T> entityType) throws IOException {
        if (InstanceInfo.class.isAssignableFrom(entityType)) {
            Eureka2.GrpcInstanceInfo grpcInstanceInfo = Eureka2.GrpcInstanceInfo.getDefaultInstance().getParserForType().parseFrom(source);
            return (T) GrpcInstanceInfoWrapper.asInstanceInfo(grpcInstanceInfo);
        }
        if (Delta.class.isAssignableFrom(entityType)) {
            Eureka2.GrpcDelta grpcDelta = Eureka2.GrpcDelta.getDefaultInstance().getParserForType().parseFrom(source);
            return (T) new GrpcDeltaWrapper<>(grpcDelta);
        }
        if (ProtocolMessageEnvelope.class.isAssignableFrom(entityType)) {
            Eureka2.GrpcProtocolMessageEnvelope grpcEnvelope = Eureka2.GrpcProtocolMessageEnvelope.getDefaultInstance().getParserForType().parseFrom(source);
            return (T) new GrpcProtocolMessageEnvelopeWrapper(grpcEnvelope);
        }
        throw new IllegalArgumentException("Grpc codec does not support type " + entityType);
    }

    private static Set<Class<?>> combined(Class<?>[]... models) {
        Set<Class<?>> result = new HashSet<>();
        for (Class<?>[] model : models) {
            Collections.addAll(result, model);
        }
        return result;
    }
}
