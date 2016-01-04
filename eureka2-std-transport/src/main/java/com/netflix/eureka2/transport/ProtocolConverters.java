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

package com.netflix.eureka2.transport;

import java.util.Map;
import java.util.Set;

import com.netflix.eureka2.model.instance.Delta;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.instance.StdInstanceInfo;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.model.notification.ModifyNotification;
import com.netflix.eureka2.model.notification.StreamStateNotification;
import com.netflix.eureka2.protocol.common.StdAddInstance;
import com.netflix.eureka2.protocol.common.StdDeleteInstance;
import com.netflix.eureka2.protocol.common.StdStreamStateUpdate;
import com.netflix.eureka2.protocol.interest.StdUpdateInstanceInfo;
import com.netflix.eureka2.spi.channel.ChannelNotification;
import com.netflix.eureka2.spi.model.TransportModel;
import com.netflix.eureka2.spi.protocol.common.AddInstance;
import com.netflix.eureka2.spi.protocol.common.DeleteInstance;
import com.netflix.eureka2.spi.protocol.common.StreamStateUpdate;
import com.netflix.eureka2.spi.protocol.interest.UpdateInstanceInfo;
import com.netflix.eureka2.protocol.ProtocolMessageEnvelope;
import com.netflix.eureka2.protocol.ProtocolMessageEnvelope.ProtocolType;

/**
 */
public final class ProtocolConverters {

    private ProtocolConverters() {
    }

    public static ChannelNotification<ChangeNotification<InstanceInfo>> asChannelNotification(ProtocolMessageEnvelope envelope,
                                                                                              Map<String, InstanceInfo> instanceCache) {
        Object message = envelope.getMessage();

        if (message instanceof AddInstance) {
            InstanceInfo instanceInfo = ((AddInstance) message).getInstanceInfo();
            instanceCache.put(instanceInfo.getId(), instanceInfo);

            ChannelNotification<ChangeNotification<InstanceInfo>> channelNotification = ChannelNotification.newData(
                    new ChangeNotification<InstanceInfo>(ChangeNotification.Kind.Add, instanceInfo)
            );
            return channelNotification;
        }
        if (message instanceof UpdateInstanceInfo) {
            Set<Delta<?>> deltas = ((UpdateInstanceInfo) message).getDeltas();
            String id = deltas.iterator().next().getId();
            InstanceInfo lastInstance = instanceCache.get(id);
            if (lastInstance == null) {
                throw new IllegalStateException("Modify update for unknown instance with id " + id);
            }
            InstanceInfo updatedInstance = lastInstance;
            for (Delta<?> delta : deltas) {
                updatedInstance = updatedInstance.applyDelta(delta);
            }
            instanceCache.put(updatedInstance.getId(), updatedInstance);

            ChannelNotification<ChangeNotification<InstanceInfo>> channelNotification = ChannelNotification.newData(
                    new ModifyNotification<>(updatedInstance, deltas)
            );
            return channelNotification;
        }
        if (message instanceof DeleteInstance) {
            String id = ((DeleteInstance) message).getInstanceId();
            InstanceInfo lastInstance = instanceCache.remove(id);
            if (lastInstance == null) {
                throw new IllegalStateException("Delete for unknown instance with id " + id);
            }
            ChannelNotification<ChangeNotification<InstanceInfo>> channelNotification = ChannelNotification.newData(
                    new ChangeNotification<>(ChangeNotification.Kind.Delete, lastInstance)
            );
            return channelNotification;
        }
        if (message instanceof StreamStateUpdate) {
            StreamStateUpdate stateUpdate = (StreamStateUpdate) message;
            ChannelNotification<ChangeNotification<InstanceInfo>> channelNotification = ChannelNotification.newData(
                    new StreamStateNotification<>(stateUpdate.getState(), stateUpdate.getInterest())
            );
            return channelNotification;
        }

        throw new IllegalStateException("Unexpected response type " + message.getClass().getName());
    }

    public static <T> ProtocolMessageEnvelope asProtocolEnvelope(ProtocolType protocolType, ChannelNotification<T> update) {
        switch (update.getKind()) {
            case Hello:
                return new ProtocolMessageEnvelope(protocolType, update.getHello());
            case Heartbeat:
                return new ProtocolMessageEnvelope(protocolType, TransportModel.getDefaultModel().creatHeartbeat());
            case Data:
                return new ProtocolMessageEnvelope(protocolType, update.getData());
        }
        throw new IllegalArgumentException("Unsupported kind " + update.getKind());
    }

    public static ProtocolMessageEnvelope asProtocolEnvelope(ProtocolType protocolType, ChangeNotification<InstanceInfo> change) {
        ProtocolMessageEnvelope envelope;
        switch (change.getKind()) {
            case Add:
                envelope = new ProtocolMessageEnvelope(protocolType, new StdAddInstance((StdInstanceInfo) change.getData()));
                break;
            case Modify:
                Set deltas = ((ModifyNotification<InstanceInfo>) change).getDelta();
                envelope = new ProtocolMessageEnvelope(protocolType, new StdUpdateInstanceInfo(deltas));
                break;
            case Delete:
                envelope = new ProtocolMessageEnvelope(protocolType, new StdDeleteInstance(change.getData().getId()));
                break;
            case BufferSentinel:
                envelope = new ProtocolMessageEnvelope(protocolType, new StdStreamStateUpdate((StreamStateNotification<InstanceInfo>) change));
                break;
            default:
                throw new IllegalStateException("Unrecognized change notification kind " + change.getKind());
        }
        return envelope;
    }
}
