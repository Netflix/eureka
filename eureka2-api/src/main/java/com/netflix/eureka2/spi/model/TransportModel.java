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

package com.netflix.eureka2.spi.model;

import com.netflix.eureka2.internal.util.ExtLoader;
import com.netflix.eureka2.model.instance.Delta;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.notification.StreamStateNotification;
import com.netflix.eureka2.spi.model.transport.Acknowledgement;
import com.netflix.eureka2.spi.model.transport.GoAway;
import com.netflix.eureka2.spi.model.transport.InterestRegistration;
import com.netflix.eureka2.spi.model.transport.ProtocolMessageEnvelope;
import com.netflix.eureka2.spi.model.transport.ProtocolMessageEnvelope.ProtocolType;
import com.netflix.eureka2.spi.model.transport.notification.AddInstance;
import com.netflix.eureka2.spi.model.transport.notification.DeleteInstance;
import com.netflix.eureka2.spi.model.transport.notification.StreamStateUpdate;
import com.netflix.eureka2.spi.model.transport.notification.UpdateInstanceInfo;

/**
 */
public abstract class TransportModel {

    private static TransportModel defaultModel;

    public abstract <T> ProtocolMessageEnvelope newEnvelope(ProtocolType protocolType, T message);

    public <T> ProtocolMessageEnvelope registrationEnvelope(T message) {
        return newEnvelope(ProtocolType.Registration, message);
    }

    public <T> ProtocolMessageEnvelope interestEnvelope(T message) {
        return newEnvelope(ProtocolType.Interest, message);
    }

    public <T> ProtocolMessageEnvelope replicationEnvelope(T message) {
        return newEnvelope(ProtocolType.Replication, message);
    }

    public abstract GoAway newGoAway();

    public abstract Acknowledgement newAcknowledgement();

    public abstract AddInstance newAddInstance(InstanceInfo instance);

    public abstract DeleteInstance newDeleteInstance(String instanceId);

    public abstract UpdateInstanceInfo newUpdateInstanceInfo(Delta<?>... delta);

    public abstract StreamStateUpdate newStreamStateUpdate(StreamStateNotification<InstanceInfo> notification);

    public abstract InterestRegistration newInterestRegistration(Interest<InstanceInfo> interest);

    public static TransportModel getDefaultModel() {
        if (defaultModel == null) {
            return ExtLoader.resolveDefaultModel().getTransportModel();
        }
        return defaultModel;
    }

    public static TransportModel setDefaultModel(TransportModel newModel) {
        TransportModel previous = defaultModel;
        defaultModel = newModel;
        return previous;
    }
}
