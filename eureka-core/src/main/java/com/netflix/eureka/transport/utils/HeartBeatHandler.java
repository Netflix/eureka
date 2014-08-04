/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.eureka.transport.utils;


import java.util.concurrent.TimeUnit;

import com.netflix.eureka.transport.MessageBroker;
import rx.Observable;

/**
 * Client/server heartbeat implementation on top of {@link MessageBroker}.
 *
 * @author Tomasz Bak
 */
public class HeartBeatHandler {

    public abstract static class HeartbeatClient<T> {
        public HeartbeatClient(MessageBroker from, long timeout, TimeUnit unit) {
        }

        /**
         * Set on error if client/server communication is broken.
         */
        public Observable<Void> connectionStatus() {
            throw new IllegalStateException("not implemented yet");
        }

        public void shutdown() {
            throw new IllegalStateException("not implemented yet");
        }

        protected abstract T heartbeatMessage();
    }

    public static class HeartbeatServer<T> {
        public HeartbeatServer(MessageBroker to, long timeout, TimeUnit unit) {
        }

        /**
         * Returns heartbeat messages, or sets error if there is a timeout.
         */
        public Observable<T> receivedHeartbeats() {
            throw new IllegalStateException("not implemented yet");
        }

        /**
         * Set on error if client/server communication is broken.
         */
        public Observable<Void> connectionStatus() {
            throw new IllegalStateException("not implemented yet");
        }

        public void shutdown() {
            throw new IllegalStateException("not implemented yet");
        }
    }
}
