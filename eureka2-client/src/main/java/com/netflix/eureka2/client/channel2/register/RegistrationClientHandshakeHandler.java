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

package com.netflix.eureka2.client.channel2.register;

import com.netflix.eureka2.client.channel2.AbstractHandshakeHandler;
import com.netflix.eureka2.model.InstanceModel;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.spi.channel.ChannelHandler;
import com.netflix.eureka2.spi.channel.ChannelNotification;
import com.netflix.eureka2.spi.channel.RegistrationHandler;
import com.netflix.eureka2.spi.model.TransportModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 */
public class RegistrationClientHandshakeHandler extends AbstractHandshakeHandler<InstanceInfo, InstanceInfo> implements RegistrationHandler {

    private static final Logger logger = LoggerFactory.getLogger(RegistrationClientHandshakeHandler.class);

    @Override
    public Observable<ChannelNotification<InstanceInfo>> handle(Observable<ChannelNotification<InstanceInfo>> inputStream) {
        return Observable.create(subscriber -> {
            logger.debug("Subscription to RegistrationClientHandshakeHandler started");

            ChannelHandler<InstanceInfo, InstanceInfo> transport = channelContext.next();
            AtomicBoolean handshakeCompleted = new AtomicBoolean();
            discoverClientId(inputStream)
                    .flatMap(clientSource -> {
                        logger.debug("Injecting hello message ahead of registration update stream");
                        ChannelNotification<InstanceInfo> helloNotification = ChannelNotification.newHello(
                                TransportModel.getDefaultModel().newClientHello(clientSource)
                        );
                        return transport.handle(Observable.just(helloNotification).mergeWith(inputStream));
                    })
                    .flatMap(handshakeVerifier(handshakeCompleted))
                    .doOnUnsubscribe(() -> logger.debug("Unsubscribing from RegistrationClientHandshakeHandler"))
                    .subscribe(subscriber);
        });
    }

    private static Observable<Source> discoverClientId(Observable<ChannelNotification<InstanceInfo>> inputStream) {
        return inputStream
                .filter(next -> next.getKind() == ChannelNotification.Kind.Data)
                .take(1)
                .map(next -> {
                    String id = next.getData().getId();
                    logger.debug("Discovered instance id {}", id);
                    return InstanceModel.getDefaultModel().createSource(Source.Origin.LOCAL, id, -1);
                });
    }
}
