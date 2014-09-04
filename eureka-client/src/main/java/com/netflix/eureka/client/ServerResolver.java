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

package com.netflix.eureka.client;

import rx.Observable;

import java.net.SocketAddress;

/**
 * Discovery of Eureka servers.
 *
 * @author Tomasz Bak
 */
public interface ServerResolver<A extends SocketAddress> {

    //TODO: We should be using ribbon's load balancers

    /**
     * Returns a stream of {@link ServerResolver.ServerEntry}
     *
     * @return A stream of {@link ServerResolver.ServerEntry}
     */
    Observable<ServerEntry<A>> resolve();

    public class ServerEntry<A> {

        public enum Action {Add, Remove}

        private final Action action;
        private final A server;

        public ServerEntry(Action action, A server) {
            this.action = action;
            this.server = server;
        }

        public Action getAction() {
            return action;
        }

        public A getServer() {
            return server;
        }
    }
}
