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

package com.netflix.eureka2.ext.grpc.model.transport;

import com.netflix.eureka2.ext.grpc.model.GrpcObjectWrapper;
import com.netflix.eureka2.ext.grpc.model.channel.*;
import com.netflix.eureka2.ext.grpc.model.instance.GrpcInstanceInfoWrapper;
import com.netflix.eureka2.ext.grpc.model.transport.notification.GrpcAddInstanceWrapper;
import com.netflix.eureka2.ext.grpc.model.transport.notification.GrpcDeleteInstanceWrapper;
import com.netflix.eureka2.ext.grpc.model.transport.notification.GrpcStreamStateUpdateWrapper;
import com.netflix.eureka2.ext.grpc.model.transport.notification.GrpcUpdateInstanceInfoWrapper;
import com.netflix.eureka2.ext.grpc.util.TextPrinter;
import com.netflix.eureka2.grpc.Eureka2;
import com.netflix.eureka2.spi.model.transport.ProtocolMessageEnvelope;

/**
 */
public class GrpcProtocolMessageEnvelopeWrapper implements ProtocolMessageEnvelope, GrpcObjectWrapper<Eureka2.GrpcProtocolMessageEnvelope> {

    private final Eureka2.GrpcProtocolMessageEnvelope grpcEnvelope;

    private volatile Object message;

    public GrpcProtocolMessageEnvelopeWrapper(Eureka2.GrpcProtocolMessageEnvelope envelope) {
        this.grpcEnvelope = envelope;
    }

    @Override
    public Eureka2.GrpcProtocolMessageEnvelope getGrpcObject() {
        return grpcEnvelope;
    }

    @Override
    public ProtocolType getProtocolType() {
        switch (grpcEnvelope.getProtocolType()) {
            case Registration:
                return ProtocolType.Registration;
            case Interest:
                return ProtocolType.Interest;
            case Replication:
                return ProtocolType.Replication;
        }
        throw new IllegalStateException("Unknown protocol type " + grpcEnvelope.getProtocolType());
    }

    @Override
    public Object getMessage() {
        if (message != null) {
            return message;
        }

        switch (grpcEnvelope.getMessageOneOfCase()) {
            case ACKNOWLEDGEMENT:
                return message = GrpcAcknowledgementWrapper.getInstance();
            case GOAWAY:
                return message = GrpcGoAwayWrapper.getInstance();
            case HEARTBEAT:
                return message = GrpcHeartbeatWrapper.getInstance();
            case CLIENTHELLO:
                return message = GrpcClientHelloWrapper.asClientHello(grpcEnvelope.getClientHello());
            case REPLICATIONCLIENTHELLO:
                return message = GrpcReplicationClientHelloWrapper.asClientHello(grpcEnvelope.getReplicationClientHello());
            case SERVERHELLO:
                return message = GrpcServerHelloWrapper.asServerHello(grpcEnvelope.getServerHello());
            case REPLICATIONSERVERHELLO:
                return message = GrpcReplicationServerHelloWrapper.asServerHello(grpcEnvelope.getReplicationServerHello());
            case INSTANCEINFO:
                return message = GrpcInstanceInfoWrapper.asInstanceInfo(grpcEnvelope.getInstanceInfo());
            case INTERESTREGISTRATION:
                return message = GrpcInterestRegistrationWrapper.asInterestRegistration(grpcEnvelope.getInterestRegistration());
            case ADDINSTANCE:
                return message = GrpcAddInstanceWrapper.asAddInstance(grpcEnvelope.getAddInstance());
            case DELETEINSTANCE:
                return message = GrpcDeleteInstanceWrapper.asDeleteInstance(grpcEnvelope.getDeleteInstance());
            case UPDATEINSTANCEINFO:
                return message = GrpcUpdateInstanceInfoWrapper.asUpdateInstanceInfo(grpcEnvelope.getUpdateInstanceInfo());
            case STREAMSTATEUPDATE:
                return message = GrpcStreamStateUpdateWrapper.asStreamStateUpdate(grpcEnvelope.getStreamStateUpdate());
        }

        throw new IllegalStateException("Unrecognized message type " + grpcEnvelope.getMessageOneOfCase());
    }

    @Override
    public int hashCode() {
        return grpcEnvelope.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof GrpcProtocolMessageEnvelopeWrapper) {
            return grpcEnvelope.equals(((GrpcProtocolMessageEnvelopeWrapper) obj).getGrpcObject());
        }
        return false;
    }

    @Override
    public String toString() {
        return TextPrinter.toString(grpcEnvelope);
    }

    public static <T> ProtocolMessageEnvelope newProtocolMessageEnvelope(ProtocolType protocolType, T message) {
        Eureka2.GrpcProtocolMessageEnvelope.Builder builder = Eureka2.GrpcProtocolMessageEnvelope.newBuilder();
        if (protocolType != null) {
            switch (protocolType) {
                case Registration:
                    builder.setProtocolType(Eureka2.GrpcProtocolMessageEnvelope.GrpcProtocolType.Registration);
                    break;
                case Interest:
                    builder.setProtocolType(Eureka2.GrpcProtocolMessageEnvelope.GrpcProtocolType.Interest);
                    break;
                case Replication:
                    builder.setProtocolType(Eureka2.GrpcProtocolMessageEnvelope.GrpcProtocolType.Replication);
                    break;
                default:
                    throw new IllegalStateException("Unrecognized protocol type " + protocolType);
            }
        }
        if (message instanceof GrpcAcknowledgementWrapper) {
            builder.setAcknowledgement(Eureka2.GrpcAcknowledgement.getDefaultInstance());
        } else if (message instanceof GrpcGoAwayWrapper) {
            builder.setGoAway(Eureka2.GrpcGoAway.getDefaultInstance());
        } else if (message instanceof GrpcHeartbeatWrapper) {
            builder.setHeartbeat(Eureka2.GrpcHeartbeat.getDefaultInstance());
        } else if (message instanceof GrpcClientHelloWrapper) {
            builder.setClientHello(((GrpcClientHelloWrapper) message).getGrpcObject());
        } else if (message instanceof GrpcReplicationClientHelloWrapper) {
            builder.setReplicationClientHello(((GrpcReplicationClientHelloWrapper) message).getGrpcObject());
        } else if (message instanceof GrpcServerHelloWrapper) {
            builder.setServerHello(((GrpcServerHelloWrapper) message).getGrpcObject());
        } else if (message instanceof GrpcReplicationServerHelloWrapper) {
            builder.setReplicationServerHello(((GrpcReplicationServerHelloWrapper) message).getGrpcObject());
        } else if (message instanceof GrpcInstanceInfoWrapper) {
            builder.setInstanceInfo(((GrpcInstanceInfoWrapper) message).getGrpcObject());
        } else if (message instanceof GrpcInterestRegistrationWrapper) {
            builder.setInterestRegistration(((GrpcInterestRegistrationWrapper) message).getGrpcObject());
        } else if (message instanceof GrpcAddInstanceWrapper) {
            builder.setAddInstance(((GrpcAddInstanceWrapper) message).getGrpcObject());
        } else if (message instanceof GrpcDeleteInstanceWrapper) {
            builder.setDeleteInstance(((GrpcDeleteInstanceWrapper) message).getGrpcObject());
        } else if (message instanceof GrpcUpdateInstanceInfoWrapper) {
            builder.setUpdateInstanceInfo(((GrpcUpdateInstanceInfoWrapper) message).getGrpcObject());
        } else if (message instanceof GrpcStreamStateUpdateWrapper) {
            builder.setStreamStateUpdate(((GrpcStreamStateUpdateWrapper) message).getGrpcObject());
        } else {
            throw new IllegalArgumentException("ProtocolMessageEnvelope not expected to wrap type " + message.getClass());
        }
        return new GrpcProtocolMessageEnvelopeWrapper(builder.build());
    }
}
