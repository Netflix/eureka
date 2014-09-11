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

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.net.SocketAddress;

import rx.Observable;

/**
 * Discovery of Eureka servers.
 *
 * @author Tomasz Bak
 */
public interface ServerResolver<A extends SocketAddress> {

    enum Protocol {
        Undefined(-1), TcpRegistration(7002), TcpDiscovery(7003), WebSockets(7001);

        private final int defaultPort;

        Protocol(int defaultPort) {
            this.defaultPort = defaultPort;
        }

        public int defaultPort() {
            return defaultPort;
        }
    }

    /**
     * Returns a stream of {@link ServerEntry}
     *
     * @return A stream of {@link ServerEntry}
     */
    Observable<ServerEntry<A>> resolve();

    @PostConstruct
    void start();

    /**
     * Most resolvers will be active components doing background work. To release the resources a
     * client must call this method. Calling {@link #close()} will also complete the {@link ServerEntry}
     * observable returned by {@link #resolve()}.
     */
    @PreDestroy
    void close();

    class ServerEntry<A> {

        public Protocol getProtocol() {
            return protocol;
        }

        public enum Action {Add, Remove}

        private final Action action;
        private final A server;
        private final Protocol protocol;

        public ServerEntry(Action action, A server) {
            this(action, server, Protocol.Undefined);
        }

        public ServerEntry(Action action, A server, Protocol protocol) {
            this.action = action;
            this.server = server;
            this.protocol = protocol;
        }

        public Action getAction() {
            return action;
        }

        public A getServer() {
            return server;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            ServerEntry that = (ServerEntry) o;

            if (action != that.action) {
                return false;
            }
            if (protocol != that.protocol) {
                return false;
            }
            if (server != null ? !server.equals(that.server) : that.server != null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = action != null ? action.hashCode() : 0;
            result = 31 * result + (server != null ? server.hashCode() : 0);
            result = 31 * result + (protocol != null ? protocol.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "ServerEntry{" +
                    "action=" + action +
                    ", server=" + server +
                    ", protocol=" + protocol +
                    '}';
        }
    }
}
