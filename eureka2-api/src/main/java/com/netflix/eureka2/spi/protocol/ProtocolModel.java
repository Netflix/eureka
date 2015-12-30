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

package com.netflix.eureka2.spi.protocol;

import com.netflix.eureka2.internal.util.ExtLoader;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.instance.Delta;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.notification.StreamStateNotification;
import com.netflix.eureka2.spi.model.Acknowledgement;
import com.netflix.eureka2.spi.model.Heartbeat;
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
public abstract class ProtocolModel {

    private static ProtocolModel defaultModel;

    public abstract Register newRegister(InstanceInfo instanceInfo);

    public abstract Heartbeat newHeartbeat();

    public abstract GoAway newGoAway();

    public abstract Acknowledgement newAcknowledgement();

    public abstract AddInstance newAddInstance(InstanceInfo instance);

    public abstract DeleteInstance newDeleteInstance(String instanceId);

    public abstract UpdateInstanceInfo newUpdateInstanceInfo(Delta<?>... delta);

    public abstract StreamStateUpdate newStreamStateUpdate(StreamStateNotification<InstanceInfo> notification);

    public abstract ReplicationHello newReplicationHello(Source senderSource, int size);

    public abstract ReplicationHelloReply newReplicationHelloReply(Source replySource, boolean sendSnapshot);

    public abstract InterestRegistration newInterestRegistration(Interest<InstanceInfo> interest);

    public static ProtocolModel getDefaultModel() {
        if (defaultModel == null) {
            return ExtLoader.resolveDefaultModel().getProtocolModel();
        }
        return defaultModel;
    }

    public static ProtocolModel setDefaultModel(ProtocolModel newModel) {
        ProtocolModel previous = defaultModel;
        defaultModel = newModel;
        return previous;
    }
}
