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

package com.netflix.eureka2.client.resolver;

import rx.Observable;

/**
 * A mechanism to discovery eureka servers.
 *
 * Out of the box implementations can be created using {@link ServerResolvers}
 *
 * @author Tomasz Bak
 */
public interface ServerResolver {

    /**
     * Returns an {@link Observable} of {@link ServerResolver.Server} instances. This can be used in two ways:
     *
     * <ul>
     <li>Resolve to next best server: The returned list has the first element as the best contender for the next
     connection. So, by using {@link Observable#take(int)} with 1 element, it will return the next server to use.</li>
     <li>List all servers: Returns a finite stream of servers known to this resolver.</li>
     </ul>
     *
     * @return An {@link Observable} of {@link ServerResolver.Server} instances.
     */
    Observable<Server> resolve();

    class Server {

        private final String host;
        private final int port;

        public Server(final String host, final int port) {
            this.port = port;
            this.host = host;
        }

        public String getHost() {
            return host;
        }

        public int getPort() {
            return port;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Server)) {
                return false;
            }

            Server server = (Server) o;

            return port == server.port && !(host != null ? !host.equals(server.host) : server.host != null);

        }

        @Override
        public int hashCode() {
            int result = host != null ? host.hashCode() : 0;
            result = 31 * result + port;
            return result;
        }
    }
}
