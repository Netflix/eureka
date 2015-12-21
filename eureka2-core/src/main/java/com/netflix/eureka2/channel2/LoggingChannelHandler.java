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

import com.netflix.eureka2.spi.channel.ChannelContext;
import com.netflix.eureka2.spi.channel.ChannelHandler;
import com.netflix.eureka2.spi.channel.ChannelNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.functions.Action3;

/**
 */
public class LoggingChannelHandler<I, O> implements ChannelHandler<I, O> {

    private static final Logger logger = LoggerFactory.getLogger(LoggingChannelHandler.class);

    // slf4j does not provide these enums, so we need to define it here
    public enum LogLevel {
        ERROR, WARN, INFO, DEBUG
    }

    private final LogLevel logLevel;

    private ChannelContext<I, O> channelContext;
    private Action3<String, Object[], Throwable> logFun;

    public LoggingChannelHandler(LogLevel logLevel) {
        this.logLevel = logLevel;
    }

    @Override
    public void init(ChannelContext<I, O> channelContext) {
        if (!channelContext.hasNext()) {
            throw new IllegalStateException("Expected next handler in the pipeline");
        }
        this.channelContext = channelContext;

        String prefix = '[' + channelContext.getPipeline().getPipelineId() + "] ";
        this.logFun = (message, params, error) -> {
            String prefixedMessage = prefix + message;
            switch (logLevel) {
                case ERROR:
                    if (error != null) {
                        logger.error(prefixedMessage, error);
                    } else {
                        logger.error(prefixedMessage, params);
                    }
                    break;
                case WARN:
                    if (error != null) {
                        logger.warn(prefixedMessage, error);
                    } else {
                        logger.warn(prefixedMessage, params);
                    }
                    break;
                case INFO:
                    if (error != null) {
                        logger.info(prefixedMessage, error);
                    } else {
                        logger.info(prefixedMessage, params);
                    }
                    break;
                case DEBUG:
                    if (error != null) {
                        logger.debug(prefixedMessage, error);
                    } else {
                        logger.debug(prefixedMessage, params);
                    }
                    break;
            }
        };

    }

    @Override
    public Observable<ChannelNotification<O>> handle(Observable<ChannelNotification<I>> inputStream) {
        return channelContext.next()
                .handle(
                        inputStream
                                .doOnSubscribe(() -> logFun.call("Subscribed to input stream", null, null))
                                .doOnUnsubscribe(() -> logFun.call("Unsubscribed from input stream", null, null))
                                .doOnNext(next -> logFun.call("Sending {}", new Object[]{next.getKind()}, null))
                                .doOnError(e -> logFun.call("Input terminated with an error", null, e))
                                .doOnCompleted(() -> logFun.call("Input onCompleted", null, null))
                )
                .doOnSubscribe(() -> logFun.call("Subscribed to reply stream", null, null))
                .doOnUnsubscribe(() -> logFun.call("Unsubscribed from reply stream", null, null))
                .doOnNext(next -> {
                    switch (next.getKind()) {
                        case Heartbeat:
                            logFun.call("Received Heartbeat", null, null);
                            break;
                        case Hello:
                            logFun.call("Received Hello {}", new Object[]{next.getHello()}, null);
                            break;
                        case Data:
                            logFun.call("Received Data {}", new Object[]{next.getData()}, null);
                            break;
                    }
                })
                .doOnError(e -> logFun.call("Reply stream terminated with an error", null, e))
                .doOnCompleted(() -> logFun.call("Reply stream onCompleted", null, null));
    }
}
