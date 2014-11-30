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

import java.io.File;

/**
 * A convenience factory for creating various flavors of {@link ServerResolver}
 *
 * @author Tomasz Bak
 */
public final class ServerResolvers {

    private ServerResolvers() {
    }

    public static ServerResolver forDnsName(String dnsName, int port) {
        return new DnsServerResolver(dnsName, port);
    }

    public static ServerResolver fromWriteServer(ServerResolver writeServerResolver, String readClusterVip) {
        return new EurekaServerResolver(writeServerResolver, readClusterVip);
    }

    public static ServerResolver fromFile(File textFile) {
        return new FileServerResolver(textFile);
    }

    public static ServerResolver just(String hostName, int port) {
        return new StaticServerResolver(new ServerResolver.Server(hostName, port));
    }

    public static ServerResolver from(ServerResolver.Server... servers) {
        return new StaticServerResolver(servers);
    }

    public static ServerResolver from(ServerResolver... resolvers) {
        return new CompositeServerResolver(resolvers);
    }
}
