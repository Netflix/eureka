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

import com.netflix.eureka2.client.resolver.DnsServerResolver.DnsServerResolverBuilder;
import com.netflix.eureka2.client.resolver.EurekaServerResolver.EurekaServerResolverBuilder;
import com.netflix.eureka2.client.resolver.FileServerResolver.FileServerResolverBuilder;
import com.netflix.eureka2.client.resolver.ServerResolver.Server;
import com.netflix.eureka2.interests.Interests;
import netflix.ocelli.loadbalancer.DefaultLoadBalancerBuilder;
import rx.Observable;

/**
 * A convenience factory for creating various flavors of {@link ServerResolver}
 *
 * @author Tomasz Bak
 */
public final class ServerResolvers {

    private ServerResolvers() {
    }

    public static ServerResolver forDnsName(String dnsName, int port) {
        return new DnsServerResolverBuilder().withDomainName(dnsName).withPort(port).build();
    }

    public static ServerResolver fromWriteServer(ServerResolver writeServerResolver, String readClusterVip) {
        return new EurekaServerResolverBuilder()
                .withBootstrapResolver(writeServerResolver)
                .withReadServerInterest(Interests.forVips(readClusterVip))
                .build();
    }

    public static ServerResolver fromFile(File textFile) {
        return new FileServerResolverBuilder().withTextFile(textFile).build();
    }

    public static ServerResolver just(String hostName, int port) {
        final Observable<Server> serverObservable = Observable.just(new Server(hostName, port));
        return new ServerResolver() {
            @Override
            public Observable<Server> resolve() {
                return serverObservable;
            }

            @Override
            public void close() {
                // Np-op
            }
        };
    }

    public static ServerResolver from(ServerResolver.Server... servers) {
        return new StaticServerResolver(new DefaultLoadBalancerBuilder<Server>(null), servers);
    }

    public static ServerResolver failoverChainFrom(ServerResolver... resolvers) {
        return new ServerResolverFailoverChain(resolvers);
    }
}
