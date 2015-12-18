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

package com.netflix.eureka2.server.channel2.replication;

import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.spi.channel.ChannelContext;
import com.netflix.eureka2.spi.channel.ChannelNotification;
import com.netflix.eureka2.spi.channel.ReplicationHandler;
import com.netflix.eureka2.spi.model.ServerHello;
import com.netflix.eureka2.spi.model.TransportModel;
import rx.Observable;

/**
 */
public class ReplicationHandlerStub implements ReplicationHandler {

    private final ServerHello serverHello;

    private volatile boolean handshakeCompleted;
    private volatile int collectedChanges;

    public ReplicationHandlerStub(Source serverSource) {
        this.serverHello = TransportModel.getDefaultModel().newServerHello(serverSource);
    }

    @Override
    public void init(ChannelContext<ChangeNotification<InstanceInfo>, Void> channelContext) {
    }

    boolean isHandshakeCompleted() {
        return handshakeCompleted;
    }

    public int getCollectedChanges() {
        return collectedChanges;
    }

    @Override
    public Observable<ChannelNotification<Void>> handle(Observable<ChannelNotification<ChangeNotification<InstanceInfo>>> replicationUpdates) {
        Observable<ChannelNotification<Void>> reply = replicationUpdates.map(inputNotification -> {
            if (inputNotification.getKind() == ChannelNotification.Kind.Hello) {
                handshakeCompleted = true;
                return ChannelNotification.newHello(serverHello);
            }
            collectedChanges++;
            return null;
        });
        return reply.filter(next -> next != null);
    }
}
