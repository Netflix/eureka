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

package com.netflix.eureka2.protocol;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.StdSource;
import com.netflix.eureka2.model.instance.Delta;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.instance.StdInstanceInfo;
import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.notification.StreamStateNotification;
import com.netflix.eureka2.model.transport.StdAcknowledgement;
import com.netflix.eureka2.model.transport.StdGoAway;
import com.netflix.eureka2.protocol.common.StdAddInstance;
import com.netflix.eureka2.protocol.common.StdDeleteInstance;
import com.netflix.eureka2.protocol.common.StdHeartbeat;
import com.netflix.eureka2.protocol.common.StdStreamStateUpdate;
import com.netflix.eureka2.protocol.interest.StdInterestRegistration;
import com.netflix.eureka2.protocol.interest.StdUpdateInstanceInfo;
import com.netflix.eureka2.protocol.register.StdRegister;
import com.netflix.eureka2.protocol.replication.StdReplicationHello;
import com.netflix.eureka2.protocol.replication.StdReplicationHelloReply;
import com.netflix.eureka2.spi.model.Acknowledgement;
import com.netflix.eureka2.spi.model.Heartbeat;
import com.netflix.eureka2.spi.protocol.ProtocolModel;
import com.netflix.eureka2.spi.protocol.common.AddInstance;
import com.netflix.eureka2.spi.protocol.common.DeleteInstance;
import com.netflix.eureka2.spi.protocol.common.GoAway;
import com.netflix.eureka2.spi.protocol.common.StreamStateUpdate;
import com.netflix.eureka2.spi.protocol.interest.InterestRegistration;
import com.netflix.eureka2.spi.protocol.interest.UpdateInstanceInfo;
import com.netflix.eureka2.spi.protocol.registration.Register;
import com.netflix.eureka2.spi.protocol.replication.ReplicationHello;
import com.netflix.eureka2.spi.protocol.replication.ReplicationHelloReply;

/**
 */
public class StdProtocolModel extends ProtocolModel {
    private static final ProtocolModel INSTANCE = new StdProtocolModel();

    @Override
    public Register newRegister(InstanceInfo instanceInfo) {
        return new StdRegister((StdInstanceInfo) instanceInfo);
    }

    @Override
    public Heartbeat newHeartbeat() {
        return StdHeartbeat.INSTANCE;
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
    public ReplicationHello newReplicationHello(Source senderSource, int size) {
        return new StdReplicationHello((StdSource) senderSource, size);
    }

    @Override
    public ReplicationHelloReply newReplicationHelloReply(Source replySource, boolean sendSnapshot) {
        return new StdReplicationHelloReply((StdSource) replySource, sendSnapshot);
    }

    @Override
    public InterestRegistration newInterestRegistration(Interest<InstanceInfo> interest) {
        return new StdInterestRegistration(interest);
    }

    public static ProtocolModel getStdModel() {
        return INSTANCE;
    }
}
