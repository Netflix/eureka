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

package com.netflix.rx.eureka.client;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.netflix.rx.eureka.transport.EurekaTransports;
import rx.Observable;

/**
 * Discovery of Eureka servers.
 *
 * @author Tomasz Bak
 */
public interface ServerResolver<A extends SocketAddress> {

    /**
     * Returns a stream of {@link ServerEntry}
     *
     * @return A stream of {@link ServerEntry}
     */
    Observable<ServerEntry<A>> resolve();

    enum ProtocolType {
        Undefined(-1),
        TcpRegistration(EurekaTransports.DEFAULT_REGISTRATION_PORT),
        TcpDiscovery(EurekaTransports.DEFAULT_DISCOVERY_PORT),
        TcpReplication(EurekaTransports.DEFAULT_REPLICATION_PORT);

        private final int defaultPort;

        ProtocolType(int defaultPort) {
            this.defaultPort = defaultPort;
        }

        public int defaultPort() {
            return defaultPort;
        }
    }

    class Protocol {
        private final int port;
        private final ProtocolType protocolType;

        public Protocol(int port, ProtocolType protocolType) {
            this.port = port;
            this.protocolType = protocolType;
        }

        public int getPort() {

            return port;
        }

        public ProtocolType getProtocolType() {
            return protocolType;
        }

        public static Set<Protocol> setOf(int port, ProtocolType protocolType) {
            return Collections.singleton(new Protocol(port, protocolType));
        }
    }

    class ServerEntry<A> {

        public enum Action {Add, Remove}

        private final Action action;
        private final A server;
        private final Set<Protocol> protocols;

        public ServerEntry(Action action, A server) {
            this(action, server, (Set<Protocol>) null);
        }

        public ServerEntry(Action action, A server, Protocol protocol) {
            this(action, server, Collections.singleton(protocol));
        }

        public ServerEntry(Action action, A server, Set<Protocol> protocols) {
            this.action = action;
            this.server = server;
            this.protocols = protocols;
        }

        public Action getAction() {
            return action;
        }

        public A getServer() {
            return server;
        }

        public int getPort(ProtocolType protocolType) {
            if (protocols == null) {
                return protocolType.defaultPort;
            }
            for (Protocol p : protocols) {
                if (p.getProtocolType() == protocolType) {
                    return p.getPort();
                }
            }
            return -1;
        }

        public Set<Protocol> getProtocols() {
            return protocols;
        }

        public boolean matches(ProtocolType protocolType) {
            return getPort(protocolType) != -1;
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
            if (protocols != null ? !protocols.equals(that.protocols) : that.protocols != null) {
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
            result = 31 * result + (protocols != null ? protocols.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "ServerEntry{action=" + action + ", server=" + server + ", protocols=" + protocols + '}';
        }

        public static Set<ServerEntry<InetSocketAddress>> cancellationSet(Set<ServerEntry<InetSocketAddress>> oldSet, Set<ServerEntry<InetSocketAddress>> newSet) {
            Set<ServerEntry<InetSocketAddress>> cancelled = new HashSet<>();
            for (ServerEntry<InetSocketAddress> entry : oldSet) {
                if (!newSet.contains(entry)) {
                    cancelled.add(new ServerEntry<InetSocketAddress>(Action.Remove, entry.getServer(), entry.getProtocols()));
                }
            }
            return cancelled;
        }
    }
}
