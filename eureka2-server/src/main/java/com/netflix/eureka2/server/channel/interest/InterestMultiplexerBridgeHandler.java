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

package com.netflix.eureka2.server.channel.interest;

import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.registry.EurekaRegistryView;
import com.netflix.eureka2.server.channel.InterestNotificationMultiplexer;
import com.netflix.eureka2.channel.ChannelHandlers;
import com.netflix.eureka2.spi.channel.ChannelContext;
import com.netflix.eureka2.spi.channel.ChannelNotification;
import com.netflix.eureka2.spi.channel.InterestHandler;
import com.netflix.eureka2.utils.rx.ExtObservable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

/**
 */
public class InterestMultiplexerBridgeHandler implements InterestHandler {

    private static final Logger logger = LoggerFactory.getLogger(InterestMultiplexerBridgeHandler.class);

    private final InterestNotificationMultiplexer notificationMultiplexer;

    public InterestMultiplexerBridgeHandler(EurekaRegistryView<InstanceInfo> registry) {
        this.notificationMultiplexer = new InterestNotificationMultiplexer(registry);
    }

    @Override
    public void init(ChannelContext<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>> channelContext) {
        if (channelContext.hasNext()) {
            throw new IllegalStateException("InterestMultiplexerBridgeHandler must be the last one in the pipeline");
        }
    }

    @Override
    public Observable<ChannelNotification<ChangeNotification<InstanceInfo>>> handle(Observable<ChannelNotification<Interest<InstanceInfo>>> inputStream) {
        Observable voidInput = inputStream.doOnNext(interestNotification -> {
            if (interestNotification.getKind() != ChannelNotification.Kind.Data) {
                logger.warn("Ignoring input notification of kind {}", interestNotification.getKind());
            } else {
                logger.info(
                        "Updating interest subscription for client {} to {}",
                        ChannelHandlers.getClientSource(interestNotification),
                        interestNotification.getData()
                );
                notificationMultiplexer.update(interestNotification.getData());
            }
        }).ignoreElements();

        Observable<ChannelNotification<ChangeNotification<InstanceInfo>>> changeNotifications = notificationMultiplexer
                .changeNotifications()
                .map(change -> ChannelNotification.newData(change));
        return ExtObservable.mergeWhenAllActive(voidInput, changeNotifications);
    }
}
