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

import java.util.*;

import com.google.protobuf.Message;
import com.netflix.eureka2.ext.grpc.model.instance.GrpcAwsDataCenterInfoWrapper;
import com.netflix.eureka2.ext.grpc.model.instance.GrpcBasicDataCenterInfoWrapper;
import com.netflix.eureka2.ext.grpc.model.instance.GrpcDeltaWrapper;
import com.netflix.eureka2.ext.grpc.model.instance.GrpcInstanceInfoWrapper;
import com.netflix.eureka2.ext.grpc.model.interest.GrpcEmptyRegistryInterestWrapper;
import com.netflix.eureka2.ext.grpc.model.interest.GrpcInterestWrapper;
import com.netflix.eureka2.ext.grpc.model.interest.GrpcMultipleInterestWrapper;
import com.netflix.eureka2.ext.grpc.model.channel.GrpcClientHelloWrapper;
import com.netflix.eureka2.ext.grpc.model.channel.GrpcReplicationClientHelloWrapper;
import com.netflix.eureka2.ext.grpc.model.channel.GrpcReplicationServerHelloWrapper;
import com.netflix.eureka2.ext.grpc.model.channel.GrpcServerHelloWrapper;
import com.netflix.eureka2.grpc.Eureka2;
import com.netflix.eureka2.model.datacenter.DataCenterInfo;
import com.netflix.eureka2.model.instance.Delta;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.interest.Interests;
import com.netflix.eureka2.model.interest.MultipleInterests;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.model.notification.ModifyNotification;
import com.netflix.eureka2.model.notification.StreamStateNotification;
import com.netflix.eureka2.spi.model.channel.ClientHello;
import com.netflix.eureka2.spi.model.channel.ReplicationClientHello;
import com.netflix.eureka2.spi.model.channel.ReplicationServerHello;
import com.netflix.eureka2.spi.model.channel.ServerHello;

/**
 */
public final class GrpcModelConverters {
    private GrpcModelConverters() {
    }

    public static <I extends Message, O extends GrpcObjectWrapper<?>> O wrap(I grpcObject) {
        if (grpcObject == null) {
            return null;
        }
        if (grpcObject instanceof Eureka2.GrpcClientHello) {
            return (O) toClientHello((Eureka2.GrpcClientHello) grpcObject);
        }
        if (grpcObject instanceof Eureka2.GrpcServerHello) {
            return (O) toServerHello((Eureka2.GrpcServerHello) grpcObject);
        }
        throw new IllegalArgumentException("wrapping not implemented for type " + grpcObject.getClass());
    }

    public static <I extends GrpcObjectWrapper<?>, O extends Message> O unwrap(I wrapper) {
        if (wrapper == null) {
            return null;
        }
        if (wrapper instanceof GrpcClientHelloWrapper) {
            return (O) toGrpcClientHello((ClientHello) wrapper);
        }
        if (wrapper instanceof GrpcServerHelloWrapper) {
            return (O) toGrpcServerHello((ServerHello) wrapper);
        }
        throw new IllegalArgumentException("wrapping not implemented for type " + wrapper.getClass());
    }

    public static InstanceInfo toInstanceInfo(Eureka2.GrpcInstanceInfo grpcInstanceInfo) {
        return GrpcInstanceInfoWrapper.asInstanceInfo(grpcInstanceInfo);
    }

    public static Eureka2.GrpcInstanceInfo toGrpcInstanceInfo(InstanceInfo instanceInfo) {
        return ((GrpcInstanceInfoWrapper) instanceInfo).getGrpcObject();
    }

    public static Set<Delta<?>> toDeltas(Collection<Eureka2.GrpcDelta> grpcDeltas) {
        Set<Delta<?>> deltas = new HashSet<>();
        for (Eureka2.GrpcDelta grpcDelta : grpcDeltas) {
            deltas.add(new GrpcDeltaWrapper(grpcDelta));
        }
        return deltas;
    }

    public static Set<Eureka2.GrpcDelta> toGrpcDeltas(Set<Delta<?>> deltas) {
        Set<Eureka2.GrpcDelta> grpcDeltas = new HashSet<>();
        for (Delta<?> delta : deltas) {
            Eureka2.GrpcDelta grpcDelta = ((GrpcDeltaWrapper) delta).getGrpcObject();
            grpcDeltas.add(grpcDelta);
        }
        return grpcDeltas;
    }

    public static Interest<InstanceInfo> toInterest(List<Eureka2.GrpcInterest> grpcInterests) {
        return Interests.forSome(toAtomicInterests(grpcInterests));
    }

    public static Interest<InstanceInfo>[] toAtomicInterests(List<Eureka2.GrpcInterest> grpcInterests) {
        Interest<InstanceInfo>[] interests = new Interest[grpcInterests.size()];
        for (int i = 0; i < interests.length; i++) {
            interests[i] = GrpcInterestWrapper.toInterest(grpcInterests.get(i));
        }
        return interests;
    }

    public static Interest<InstanceInfo> toInterest(Eureka2.GrpcInterestRegistration grpcInterestRegistration) {
        List<Eureka2.GrpcInterest> grpcInterestsList = grpcInterestRegistration.getInterestsList();
        if (grpcInterestsList.isEmpty()) {
            return GrpcEmptyRegistryInterestWrapper.getInstance();
        }
        if (grpcInterestsList.size() == 1) {
            return GrpcInterestWrapper.toInterest(grpcInterestsList.get(0));
        }

        Set<Interest<InstanceInfo>> interests = new HashSet<>(grpcInterestsList.size());
        for (Eureka2.GrpcInterest grpcInterest : grpcInterestsList) {
            interests.add(GrpcInterestWrapper.toInterest(grpcInterest));
        }
        return new GrpcMultipleInterestWrapper(interests);
    }

    public static Eureka2.GrpcChangeNotification toGrpcChangeNotification(ChangeNotification<InstanceInfo> notification) {
        switch (notification.getKind()) {
            case Add:
                return Eureka2.GrpcChangeNotification.newBuilder().setAdd(
                        Eureka2.GrpcChangeNotification.GrpcAddChangeNotification.newBuilder()
                                .setInstanceInfo(GrpcInstanceInfoWrapper.asGrpcInstanceInfo(notification.getData()))
                                .build()
                ).build();
            case Modify:
                ModifyNotification<InstanceInfo> modify = (ModifyNotification<InstanceInfo>) notification;
                return Eureka2.GrpcChangeNotification.newBuilder().setModify(
                        Eureka2.GrpcChangeNotification.GrpcModifyChangeNotification.newBuilder()
                                .addAllDeltas(toGrpcDeltas(modify.getDelta()))
                ).build();
            case Delete:
                return Eureka2.GrpcChangeNotification.newBuilder().setDelete(
                        Eureka2.GrpcChangeNotification.GrpcDeleteChangeNotification.newBuilder()
                                .setInstanceId(notification.getData().getId())
                                .build()
                ).build();
            case BufferSentinel:
                StreamStateNotification<InstanceInfo> stateNotification = (StreamStateNotification<InstanceInfo>) notification;
                return Eureka2.GrpcChangeNotification.newBuilder().setBufferSentinel(
                        Eureka2.GrpcChangeNotification.GrpcBufferSentinelNotification.newBuilder()
                                .addAllInterest(toGrpcInterest(stateNotification.getInterest()))
                                .setBufferStart(stateNotification.getBufferState() == StreamStateNotification.BufferState.BufferStart)
                                .build()
                ).build();
        }
        throw new IllegalStateException("Notification type " + notification.getKind() + " not implemented yet");
    }

    public static ChangeNotification<InstanceInfo> toChangeNotification(Eureka2.GrpcChangeNotification grpcNotification,
                                                                        Map<String, InstanceInfo> instanceCache) {
        switch (grpcNotification.getNotificationOneofCase()) {
            case ADD:
                InstanceInfo instance = GrpcInstanceInfoWrapper.asInstanceInfo(grpcNotification.getAdd().getInstanceInfo());
                instanceCache.put(instance.getId(), instance);
                return new ChangeNotification<InstanceInfo>(ChangeNotification.Kind.Add, instance);
            case MODIFY:
                Set<Delta<?>> deltas = toDeltas(grpcNotification.getModify().getDeltasList());
                String modifyId = deltas.iterator().next().getId();
                InstanceInfo modifyCopy = instanceCache.get(modifyId);
                if (modifyCopy == null) {
                    return null;
                }
                InstanceInfo newCopy = modifyCopy;
                for (Delta<?> delta : deltas) {
                    newCopy = newCopy.applyDelta(delta);
                }
                instanceCache.put(modifyId, newCopy);
                return new ModifyNotification<>(newCopy, deltas);
            case DELETE:
                String id = grpcNotification.getDelete().getInstanceId();
                InstanceInfo lastCopy = instanceCache.get(id);
                if (lastCopy == null) {
                    return null;
                }
                instanceCache.remove(id);
                return new ChangeNotification<InstanceInfo>(ChangeNotification.Kind.Delete, lastCopy);
            case BUFFERSENTINEL:
                StreamStateNotification.BufferState state = grpcNotification.getBufferSentinel().getBufferStart()
                        ? StreamStateNotification.BufferState.BufferStart
                        : StreamStateNotification.BufferState.BufferEnd;
                Interest<InstanceInfo> interest = toInterest(grpcNotification.getBufferSentinel().getInterestList());
                return new StreamStateNotification<InstanceInfo>(state, interest);
        }
        throw new IllegalArgumentException("Unrecognized change notification type " + grpcNotification.getNotificationOneofCase());
    }

    public static List<Eureka2.GrpcInterest> toGrpcInterest(Interest<InstanceInfo> interest) {
        List<Eureka2.GrpcInterest> allInterests = new ArrayList<>();

        if (interest instanceof MultipleInterests) {
            for (Interest<InstanceInfo> atomic : ((MultipleInterests<InstanceInfo>) interest).flatten()) {
                GrpcInterestWrapper interestWrapper = (GrpcInterestWrapper) atomic;
                allInterests.add(interestWrapper.getGrpcObject());
            }
        } else {
            GrpcInterestWrapper interestWrapper = (GrpcInterestWrapper) interest;
            allInterests.add(interestWrapper.getGrpcObject());
        }
        return allInterests;
    }

    public static Eureka2.GrpcInterestRegistration toGrpcInterestRegistration(Interest<InstanceInfo> interest) {
        Eureka2.GrpcInterestRegistration.Builder builder = Eureka2.GrpcInterestRegistration.newBuilder();

        if (interest instanceof MultipleInterests) {
            for (Interest<InstanceInfo> atomic : ((MultipleInterests<InstanceInfo>) interest).flatten()) {
                GrpcInterestWrapper interestWrapper = (GrpcInterestWrapper) atomic;
                builder.addInterests(interestWrapper.getGrpcObject());
            }
        } else {
            GrpcInterestWrapper interestWrapper = (GrpcInterestWrapper) interest;
            builder.addInterests(interestWrapper.getGrpcObject());
        }
        return builder.build();
    }

    public static Eureka2.GrpcClientHello toGrpcClientHello(ClientHello clientHello) {
        return ((GrpcClientHelloWrapper) clientHello).getGrpcObject();
    }

    public static Eureka2.GrpcReplicationClientHello toGrpcReplicationClientHello(ReplicationClientHello clientHello) {
        return ((GrpcReplicationClientHelloWrapper) clientHello).getGrpcObject();
    }

    public static Eureka2.GrpcServerHello toGrpcServerHello(ServerHello serverHello) {
        return ((GrpcServerHelloWrapper) serverHello).getGrpcObject();
    }

    public static Eureka2.GrpcReplicationServerHello toGrpcReplicationServerHello(ReplicationServerHello serverHello) {
        return ((GrpcReplicationServerHelloWrapper) serverHello).getGrpcObject();
    }

    public static ClientHello toClientHello(Eureka2.GrpcClientHello grpcClientHello) {
        return GrpcClientHelloWrapper.asClientHello(grpcClientHello);
    }

    public static ClientHello toReplicationClientHello(Eureka2.GrpcReplicationClientHello grpcClientHello) {
        return GrpcReplicationClientHelloWrapper.asClientHello(grpcClientHello);
    }

    public static ServerHello toServerHello(Eureka2.GrpcServerHello grpcServerHello) {
        return GrpcServerHelloWrapper.asServerHello(grpcServerHello);
    }

    public static ServerHello toReplicationServerHello(Eureka2.GrpcReplicationServerHello grpcServerHello) {
        return GrpcReplicationServerHelloWrapper.asServerHello(grpcServerHello);
    }

    public static DataCenterInfo toDataCenterInfo(Eureka2.GrpcDataCenterInfo dataCenterInfo) {
        Eureka2.GrpcDataCenterInfo.OneofDataCenterInfoCase dataCenterType = dataCenterInfo.getOneofDataCenterInfoCase();
        switch (dataCenterType) {
            case AWS:
                return new GrpcAwsDataCenterInfoWrapper(dataCenterInfo);
            case BASIC:
                return new GrpcBasicDataCenterInfoWrapper(dataCenterInfo);
        }
        throw new IllegalStateException("Unsupported datacenter type " + dataCenterType);
    }
}
