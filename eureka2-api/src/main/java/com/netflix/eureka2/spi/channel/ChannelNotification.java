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

package com.netflix.eureka2.spi.channel;

import java.util.HashMap;
import java.util.Map;

/**
 */
public interface ChannelNotification<DATA> {

    enum Kind {
        Hello,
        Heartbeat,
        Data,
        Disconnected
    }

    Kind getKind();

    DATA getData();

    <HELLO> HELLO getHello();

    Map<String, Object> getContext();

    static <HELLO, DATA> ChannelNotification<DATA> newHello(HELLO hello) {
        return new ChannelNotification<DATA>() {
            private final HashMap<String, Object> context = new HashMap<>();

            @Override
            public Kind getKind() {
                return Kind.Hello;
            }

            @Override
            public DATA getData() {
                return null;
            }

            @Override
            public <HELLO> HELLO getHello() {
                return (HELLO) hello;
            }

            @Override
            public Map<String, Object> getContext() {
                return context;
            }
        };
    }

    static <DATA> ChannelNotification<DATA> newHeartbeat() {
        return new ChannelNotification<DATA>() {
            private final HashMap<String, Object> context = new HashMap<>();

            @Override
            public Kind getKind() {
                return Kind.Heartbeat;
            }

            @Override
            public DATA getData() {
                return null;
            }

            @Override
            public <HELLO> HELLO getHello() {
                return null;
            }

            @Override
            public Map<String, Object> getContext() {
                return context;
            }
        };
    }

    static <DATA> ChannelNotification<DATA> newData(DATA data) {
        return new ChannelNotification<DATA>() {
            private final HashMap<String, Object> context = new HashMap<>();

            @Override
            public Kind getKind() {
                return Kind.Data;
            }

            @Override
            public DATA getData() {
                return data;
            }

            @Override
            public <HELLO> HELLO getHello() {
                return null;
            }

            @Override
            public Map<String, Object> getContext() {
                return context;
            }
        };
    }
}
