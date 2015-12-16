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

package com.netflix.eureka2.channel2;

import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.spi.channel.ChannelContext;
import com.netflix.eureka2.spi.channel.ChannelHandler;
import com.netflix.eureka2.spi.channel.ChannelNotification;
import rx.Observable;

import static com.netflix.eureka2.model.notification.SourcedChangeNotification.toSourced;

/**
 */
public class InputChangeNotificationSourcingHandler<T, O> implements ChannelHandler<ChangeNotification<T>, O> {

    private ChannelContext<ChangeNotification<T>, O> channelContext;

    @Override
    public void init(ChannelContext<ChangeNotification<T>, O> channelContext) {
        if (!channelContext.hasNext()) {
            throw new IllegalStateException("Expected next handler in pipeline");
        }
        this.channelContext = channelContext;
    }

    @Override
    public Observable<ChannelNotification<O>> handle(Observable<ChannelNotification<ChangeNotification<T>>> inputStream) {
        return channelContext.next().handle(
                inputStream.map(notification -> {
                    if (notification.getKind() != ChannelNotification.Kind.Data) {
                        return notification;
                    }
                    ChangeNotification<T> sourced = toSourced(notification.getData(), ChannelHandlers.getClientSource(notification));
                    return notification.setData(sourced);
                })
        );
    }
}
