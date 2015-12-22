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
import java.util.HashMap;
import java.util.Map;

import com.netflix.eureka2.model.Server;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.spi.channel.ChannelNotification;
import com.netflix.eureka2.spi.channel.InterestHandler;
import com.netflix.eureka2.spi.model.Heartbeat;
import com.netflix.eureka2.spi.model.ServerHello;
import com.netflix.eureka2.spi.protocol.ProtocolModel;
import com.netflix.eureka2.transport.ProtocolConverters;
import com.netflix.eureka2.protocol.ProtocolMessageEnvelope;
import com.netflix.eureka2.protocol.ProtocolMessageEnvelope.ProtocolType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

import static com.netflix.eureka2.protocol.ProtocolMessageEnvelope.interestEnvelope;

/**
 */
public class StdInterestClientTransportHandler extends AbstractStdClientTransportHandler<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>> implements InterestHandler {

    private static final Logger logger = LoggerFactory.getLogger(StdInterestClientTransportHandler.class);

    public StdInterestClientTransportHandler(Server server) {
        super(server, ProtocolType.Interest);
    }

    @Override
    public Observable<ChannelNotification<ChangeNotification<InstanceInfo>>> handle(Observable<ChannelNotification<Interest<InstanceInfo>>> inputStream) {
        return connect().take(1).flatMap(connection -> {
            logger.debug("Subscribed to StdInterestClientTransportHandler handler");

            Map<String, InstanceInfo> instanceCache = new HashMap<>();

            Observable output = inputStream.flatMap(notification -> {
                return connection.writeAndFlush(asProtocolMessage(notification));
            });

            Observable<ChannelNotification<ChangeNotification<InstanceInfo>>> input = connection.getInput().flatMap(next -> {
                ProtocolMessageEnvelope envelope = (ProtocolMessageEnvelope) next;
                return asChannelNotification(envelope, instanceCache);
            });

            return Observable.merge(output, input);
        });
    }

    private Observable<ChannelNotification<ChangeNotification<InstanceInfo>>> asChannelNotification(ProtocolMessageEnvelope envelope,
                                                                                                    Map<String, InstanceInfo> instanceCache) {
        Object message = envelope.getMessage();

        if (envelope.getProtocolType() != ProtocolType.Interest) {
            String error = "Non-interest protocol message received " + message.getClass().getName();
            logger.error(error);
            return Observable.error(new IOException(error));
        }

        if (message instanceof Heartbeat) {
            return Observable.just(ChannelNotification.newHeartbeat());
        }
        if (message instanceof ServerHello) {
            return Observable.just(ChannelNotification.newHello(message));
        }
        try {
            return Observable.just(ProtocolConverters.asChannelNotification(envelope, instanceCache));
        } catch (Exception e) {
            return Observable.error(e);
        }
    }

    @Override
    protected ProtocolMessageEnvelope asProtocolMessage(ChannelNotification<Interest<InstanceInfo>> update) {
        if (update.getKind() == ChannelNotification.Kind.Data) {
            return interestEnvelope(ProtocolModel.getDefaultModel().newInterestRegistration(update.getData()));
        }
        return super.asProtocolMessage(update);
    }
}
