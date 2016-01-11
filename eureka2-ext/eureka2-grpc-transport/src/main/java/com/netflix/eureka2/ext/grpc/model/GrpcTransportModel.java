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

package com.netflix.eureka2.ext.grpc.model;

import java.util.Collections;
import java.util.HashSet;

import com.netflix.eureka2.ext.grpc.model.transport.GrpcAcknowledgementWrapper;
import com.netflix.eureka2.ext.grpc.model.transport.GrpcGoAwayWrapper;
import com.netflix.eureka2.ext.grpc.model.transport.GrpcInterestRegistrationWrapper;
import com.netflix.eureka2.ext.grpc.model.transport.GrpcProtocolMessageEnvelopeWrapper;
import com.netflix.eureka2.ext.grpc.model.transport.notification.GrpcAddInstanceWrapper;
import com.netflix.eureka2.ext.grpc.model.transport.notification.GrpcDeleteInstanceWrapper;
import com.netflix.eureka2.ext.grpc.model.transport.notification.GrpcStreamStateUpdateWrapper;
import com.netflix.eureka2.ext.grpc.model.transport.notification.GrpcUpdateInstanceInfoWrapper;
import com.netflix.eureka2.model.instance.Delta;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.notification.StreamStateNotification;
import com.netflix.eureka2.spi.model.TransportModel;
import com.netflix.eureka2.spi.model.transport.Acknowledgement;
import com.netflix.eureka2.spi.model.transport.GoAway;
import com.netflix.eureka2.spi.model.transport.InterestRegistration;
import com.netflix.eureka2.spi.model.transport.ProtocolMessageEnvelope;
import com.netflix.eureka2.spi.model.transport.notification.AddInstance;
import com.netflix.eureka2.spi.model.transport.notification.DeleteInstance;
import com.netflix.eureka2.spi.model.transport.notification.StreamStateUpdate;
import com.netflix.eureka2.spi.model.transport.notification.UpdateInstanceInfo;

/**
 */
public class GrpcTransportModel extends TransportModel {
    private static final TransportModel INSTANCE = new GrpcTransportModel();

    @Override
    public <T> ProtocolMessageEnvelope newEnvelope(ProtocolMessageEnvelope.ProtocolType protocolType, T message) {
        return GrpcProtocolMessageEnvelopeWrapper.newProtocolMessageEnvelope(protocolType, message);
    }

    @Override
    public GoAway newGoAway() {
        return GrpcGoAwayWrapper.getInstance();
    }

    @Override
    public Acknowledgement newAcknowledgement() {
        return GrpcAcknowledgementWrapper.getInstance();
    }

    @Override
    public AddInstance newAddInstance(InstanceInfo instance) {
        return GrpcAddInstanceWrapper.newAddInstance(instance);
    }

    @Override
    public DeleteInstance newDeleteInstance(String instanceId) {
        return GrpcDeleteInstanceWrapper.newDeleteInstance(instanceId);
    }

    @Override
    public UpdateInstanceInfo newUpdateInstanceInfo(Delta<?>... deltas) {
        HashSet<Delta<?>> deltaSet = new HashSet<>();
        Collections.addAll(deltaSet, deltas);
        return GrpcUpdateInstanceInfoWrapper.newUpdateInstanceInfo(deltaSet);
    }

    @Override
    public StreamStateUpdate newStreamStateUpdate(StreamStateNotification<InstanceInfo> notification) {
        return GrpcStreamStateUpdateWrapper.newStreamStateUpdate(notification.getBufferState(), notification.getInterest());
    }

    @Override
    public InterestRegistration newInterestRegistration(Interest<InstanceInfo> interest) {
        return GrpcInterestRegistrationWrapper.newInterestRegistration(interest);
    }

    public static TransportModel getGrpcModel() {
        return INSTANCE;
    }
}
