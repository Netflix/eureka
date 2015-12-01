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

package com.netflix.eureka2.client.channel2.interest;

import com.netflix.eureka2.client.channel2.AbstractHandshakeHandler;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.spi.channel.ChannelNotification;
import com.netflix.eureka2.spi.channel.InterestHandler;
import com.netflix.eureka2.spi.model.TransportModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 */
public class InterestClientHandshakeHandler extends AbstractHandshakeHandler<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>> implements InterestHandler {

    private static final Logger logger = LoggerFactory.getLogger(InterestClientHandshakeHandler.class);

    private final ChannelNotification<Interest<InstanceInfo>> clientHello;

    public InterestClientHandshakeHandler(Source clientSource) {
        this.clientHello = ChannelNotification.newHello(TransportModel.getDefaultModel().newClientHello(clientSource));
    }

    @Override
    public Observable<ChannelNotification<ChangeNotification<InstanceInfo>>> handle(Observable<ChannelNotification<Interest<InstanceInfo>>> inputStream) {
        return Observable.create(subscriber -> {
            logger.debug("Subscription to InterestClientHandshakeHandler started");

            AtomicBoolean handshakeCompleted = new AtomicBoolean();
            channelContext.next().handle(Observable.just(clientHello).concatWith(inputStream))
                    .flatMap(handshakeVerifier(handshakeCompleted))
                    .doOnUnsubscribe(() -> logger.debug("Unsubscribing from InterestClientHandshakeHandler"))
                    .subscribe(subscriber);
        });
    }
}
