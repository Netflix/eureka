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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 */
public abstract class ChannelNotification<DATA> {

    public enum Kind {
        Hello,
        Heartbeat,
        Data,
        Disconnected
    }

    private final Kind kind;
    private final Map<String, Object> context;

    protected ChannelNotification(Kind kind, Map<String, Object> context) {
        this.kind = kind;
        this.context = context;
    }

    public Kind getKind() {
        return kind;
    }

    public Map<String, Object> getContext() {
        return context == null ? Collections.emptyMap() : context;
    }

    public abstract DATA getData();

    public abstract <HELLO> HELLO getHello();

    public ChannelNotification<DATA> setContext(String key, Object value) {
        Map<String, Object> newContext;
        if (context == null) {
            newContext = Collections.singletonMap(key, value);
        } else {
            newContext = new HashMap<>(context);
            newContext.put(key, value);
            newContext = Collections.unmodifiableMap(newContext);
        }
        switch (kind) {
            case Hello:
                return newHello(getHello(), newContext);
            case Heartbeat:
                return newHeartbeat(newContext);
            case Data:
                return newData(getData(), newContext);
            case Disconnected:
                throw new IllegalStateException("not implemented yet");
        }
        throw new IllegalStateException("not supported kind " + kind);
    }

    public abstract ChannelNotification<DATA> setData(DATA data);

    public static <HELLO, DATA> ChannelNotification<DATA> newHello(HELLO hello) {
        return newHello(hello, null);
    }

    public static <HELLO, DATA> ChannelNotification<DATA> newHello(HELLO hello, Map<String, Object> context) {
        return new ChannelNotification<DATA>(Kind.Hello, context) {

            @Override
            public DATA getData() {
                return null;
            }

            @Override
            public <HELLO> HELLO getHello() {
                return (HELLO) hello;
            }

            @Override
            public ChannelNotification<DATA> setData(DATA data) {
                throw new IllegalStateException("Data update on Hello channel notification");
            }
        };
    }

    public static <DATA> ChannelNotification<DATA> newHeartbeat() {
        return newHeartbeat(null);
    }

    public static <DATA> ChannelNotification<DATA> newHeartbeat(Map<String, Object> context) {
        return new ChannelNotification<DATA>(Kind.Heartbeat, context) {

            @Override
            public DATA getData() {
                return null;
            }

            @Override
            public <HELLO> HELLO getHello() {
                return null;
            }

            @Override
            public ChannelNotification<DATA> setData(DATA data) {
                throw new IllegalStateException("Data update on Hello channel notification");
            }
        };
    }

    public static <DATA> ChannelNotification<DATA> newData(DATA data) {
        return newData(data, null);
    }

    public static <DATA> ChannelNotification<DATA> newData(DATA data, Map<String, Object> context) {
        return new ChannelNotification<DATA>(Kind.Data, context) {
            @Override
            public DATA getData() {
                return data;
            }

            @Override
            public <HELLO> HELLO getHello() {
                return null;
            }

            @Override
            public ChannelNotification<DATA> setData(DATA data) {
                return newData(data, getContext());
            }
        };
    }
}
