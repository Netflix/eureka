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

package com.netflix.eureka2.model;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.netflix.eureka2.model.instance.Delta;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.instance.StdInstanceInfo;
import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.notification.StreamStateNotification;
import com.netflix.eureka2.model.transport.StdAcknowledgement;
import com.netflix.eureka2.model.transport.StdGoAway;
import com.netflix.eureka2.model.transport.StdInterestRegistration;
import com.netflix.eureka2.model.transport.StdProtocolMessageEnvelope;
import com.netflix.eureka2.model.transport.notification.StdAddInstance;
import com.netflix.eureka2.model.transport.notification.StdDeleteInstance;
import com.netflix.eureka2.model.transport.notification.StdStreamStateUpdate;
import com.netflix.eureka2.model.transport.notification.StdUpdateInstanceInfo;
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
public class StdTransportModel extends TransportModel {
    private static final TransportModel INSTANCE = new StdTransportModel();

    @Override
    public <T> ProtocolMessageEnvelope newEnvelope(ProtocolMessageEnvelope.ProtocolType protocolType, T message) {
        return new StdProtocolMessageEnvelope(protocolType, message);
    }

    @Override
    public GoAway newGoAway() {
        return StdGoAway.INSTANCE;
    }

    @Override
    public Acknowledgement newAcknowledgement() {
        return StdAcknowledgement.INSTANCE;
    }

    @Override
    public AddInstance newAddInstance(InstanceInfo instance) {
        return new StdAddInstance((StdInstanceInfo) instance);
    }

    @Override
    public DeleteInstance newDeleteInstance(String instanceId) {
        return new StdDeleteInstance(instanceId);
    }

    @Override
    public UpdateInstanceInfo newUpdateInstanceInfo(Delta<?>... deltas) {
        Set deltaSet = new HashSet<>();
        Collections.addAll(deltaSet, deltas);
        return new StdUpdateInstanceInfo(deltaSet);
    }

    @Override
    public StreamStateUpdate newStreamStateUpdate(StreamStateNotification<InstanceInfo> notification) {
        return new StdStreamStateUpdate(notification);
    }

    @Override
    public InterestRegistration newInterestRegistration(Interest<InstanceInfo> interest) {
        return new StdInterestRegistration(interest);
    }

    public static TransportModel getStdModel() {
        return INSTANCE;
    }
}
