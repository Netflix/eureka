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

package com.netflix.eureka2.client.channel.interest;

import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.spi.channel.ChannelContext;
import com.netflix.eureka2.spi.channel.ChannelNotification;
import com.netflix.eureka2.spi.channel.InterestHandler;
import com.netflix.eureka2.spi.model.channel.ServerHello;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

/**
 * Detect bad configurations when a read server connects to itself.
 */
public class InterestLoopDetectorHandler implements InterestHandler {

    private static final Logger logger = LoggerFactory.getLogger(InterestLoopDetectorHandler.class);

    private ChannelContext<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>> channelContext;

    private final Source clientSource;

    public InterestLoopDetectorHandler(Source clientSource) {
        this.clientSource = clientSource;
    }

    @Override
    public void init(ChannelContext<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>> channelContext) {
        if (!channelContext.hasNext()) {
            throw new IllegalStateException("InterestLoopDetectorHandler cannot be the last one in the pipeline");
        }
        this.channelContext = channelContext;
    }

    @Override
    public Observable<ChannelNotification<ChangeNotification<InstanceInfo>>> handle(Observable<ChannelNotification<Interest<InstanceInfo>>> inputStream) {
        return channelContext.next().handle(inputStream).flatMap(next -> {
            if (next.getKind() == ChannelNotification.Kind.Hello) {
                ServerHello serverHello = next.getHello();
                Source serverSource = serverHello.getServerSource();
                if (clientSource.getName().equals(serverSource.getName())) {
                    logger.info("Interest loop detected; disconnecting the interest channel");
                    return Observable.error(InterestLoopException.INSTANCE);
                }
            }
            return Observable.just(next);
        });
    }
}
