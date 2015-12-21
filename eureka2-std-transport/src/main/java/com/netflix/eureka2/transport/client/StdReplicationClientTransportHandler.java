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

package com.netflix.eureka2.transport.client;

import java.io.IOException;

import com.netflix.eureka2.model.Server;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.spi.channel.ChannelNotification;
import com.netflix.eureka2.spi.channel.ReplicationHandler;
import com.netflix.eureka2.spi.model.Heartbeat;
import com.netflix.eureka2.spi.model.ReplicationServerHello;
import com.netflix.eureka2.transport.ProtocolConverters;
import com.netflix.eureka2.transport.codec.ProtocolMessageEnvelope;
import com.netflix.eureka2.transport.codec.ProtocolMessageEnvelope.ProtocolType;
import io.reactivex.netty.channel.ObservableConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

/**
 */
public class StdReplicationClientTransportHandler extends AbstractStdClientTransportHandler<ChangeNotification<InstanceInfo>, Void> implements ReplicationHandler {

    private static final Logger logger = LoggerFactory.getLogger(StdReplicationClientTransportHandler.class);

    public StdReplicationClientTransportHandler(Server server) {
        super(server, ProtocolType.Replication);
    }

    @Override
    public Observable<ChannelNotification<Void>> handle(Observable<ChannelNotification<ChangeNotification<InstanceInfo>>> inputStream) {
        return connect().take(1).flatMap(connection -> {
            logger.debug("Subscribed to StdReplicationClientTransportHandler handler");

            Observable output = inputStream.flatMap(notification -> writeChannelNotification(connection, notification));

            Observable<ChannelNotification<Void>> input = connection.getInput().flatMap(next -> {
                return asChannelNotification((ProtocolMessageEnvelope) next);
            });

            return Observable.merge(output, input);
        });
    }

    private static Observable<ChannelNotification<Void>> asChannelNotification(ProtocolMessageEnvelope envelope) {
        Object message = envelope.getMessage();

        if (envelope.getProtocolType() != ProtocolType.Replication) {
            String error = "Non-replication protocol message received " + message.getClass().getName();
            logger.error(error);
            return Observable.error(new IOException(error));
        }

        if (message instanceof Heartbeat) {
            return Observable.just(ChannelNotification.newHeartbeat());
        }
        if (message instanceof ReplicationServerHello) {
            return Observable.just(ChannelNotification.newHello(message));
        }

        return Observable.error(new IllegalStateException("Unexpected response type " + message.getClass().getName()));
    }

    private Observable<Void> writeChannelNotification(ObservableConnection<Object, Object> connection,
                                                      ChannelNotification<ChangeNotification<InstanceInfo>> notification) {
        if (notification.getKind() != ChannelNotification.Kind.Data) {
            return connection.writeAndFlush(asProtocolMessage(notification));
        }
        ProtocolMessageEnvelope envelope = ProtocolConverters.asProtocolEnvelope(ProtocolType.Replication, notification.getData());
        return connection.writeAndFlush(envelope);
    }
}
