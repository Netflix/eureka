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
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.netflix.eureka2.model.Server;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.spi.channel.ChannelNotification;
import com.netflix.eureka2.spi.channel.RegistrationHandler;
import com.netflix.eureka2.spi.model.TransportModel;
import com.netflix.eureka2.spi.model.channel.Heartbeat;
import com.netflix.eureka2.spi.model.channel.ServerHello;
import com.netflix.eureka2.spi.model.transport.Acknowledgement;
import com.netflix.eureka2.spi.model.transport.ProtocolMessageEnvelope;
import com.netflix.eureka2.spi.model.transport.ProtocolMessageEnvelope.ProtocolType;
import com.netflix.eureka2.transport.TransportDisconnected;
import com.netflix.eureka2.utils.rx.ExtObservable;
import io.reactivex.netty.channel.ObservableConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

/**
 */
public class StdRegistrationClientTransportHandler extends AbstractStdClientTransportHandler<InstanceInfo, InstanceInfo> implements RegistrationHandler {

    private static final Logger logger = LoggerFactory.getLogger(StdRegistrationClientTransportHandler.class);

    private static final TransportDisconnected CONNECTION_CLOSED = new TransportDisconnected("Registration client connection closed");

    public StdRegistrationClientTransportHandler(Server server) {
        super(server, ProtocolType.Registration);
    }

    @Override
    public Observable<ChannelNotification<InstanceInfo>> handle(Observable<ChannelNotification<InstanceInfo>> registrationUpdates) {
        return connect().take(1).flatMap(connection -> {

            logger.debug("Subscribed to StdRegistrationClientTransportHandler handler");

            Queue<InstanceInfo> updatesQueue = new ConcurrentLinkedQueue<InstanceInfo>();

            Observable output = registrationUpdates.flatMap(update -> {
                if (update.getKind() == ChannelNotification.Kind.Data) {
                    updatesQueue.add(update.getData());
                }
                return connection.writeAndFlush(asProtocolMessage(update));
            }).doOnUnsubscribe(() -> doGracefulShutdown(connection));

            Observable<ChannelNotification<InstanceInfo>> input = connection.getInput().flatMap(next -> {
                ProtocolMessageEnvelope envelope = (ProtocolMessageEnvelope) next;
                return asChannelNotification(envelope, updatesQueue);
            }).concatWith(Observable.error(CONNECTION_CLOSED));

            return ExtObservable.mergeWhenAllActive(output, input);
        });
    }

    private void doGracefulShutdown(ObservableConnection<Object, Object> connection) {
        connection.writeAndFlush(TransportModel.getDefaultModel().registrationEnvelope(TransportModel.getDefaultModel().newGoAway()))
                .subscribe(
                        next -> {
                            // Void
                        },
                        e -> logger.debug("Graceful shutdown completed with an error: {}", e.getMessage() == null ? e.getClass().getName() : e.getMessage())
                );
        connection.close();
    }

    private static Observable<ChannelNotification<InstanceInfo>> asChannelNotification(ProtocolMessageEnvelope envelope,
                                                                                       Queue<InstanceInfo> updatesQueue) {
        Object message = envelope.getMessage();

        if (envelope.getProtocolType() != ProtocolType.Registration) {
            String error = "Non-registration protocol message received " + message.getClass().getName();
            logger.error(error);
            return Observable.error(new IOException(error));
        }

        if (message instanceof Heartbeat) {
            return Observable.just(ChannelNotification.newHeartbeat());
        }
        if (message instanceof ServerHello) {
            return Observable.just(ChannelNotification.newHello(message));
        }
        if (message instanceof Acknowledgement) {
            InstanceInfo confirmed = updatesQueue.poll();
            if (confirmed == null) {
                return Observable.error(new IllegalStateException("Received unexpected acknowledgement in the registration channel"));
            } else {
                return Observable.just(ChannelNotification.newData(confirmed));
            }
        }

        return Observable.error(new IllegalStateException("Unexpected response type " + message.getClass().getName()));
    }
}
