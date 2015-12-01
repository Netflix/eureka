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

package com.netflix.eureka2.server.channel2;

import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.spi.channel.ChannelNotification;

/**
 */
public final class ServerHandlers {

    public static final String CLIENT_SOURCE = "client.source";

    private ServerHandlers() {
    }

    public static <T> Source getClientSource(ChannelNotification<T> notification) {
        return (Source) notification.getContext().get(CLIENT_SOURCE);
    }

    public static <T> void setClientSource(ChannelNotification<T> notification, Source clientSource) {
        notification.getContext().put(CLIENT_SOURCE, clientSource);
    }
}
