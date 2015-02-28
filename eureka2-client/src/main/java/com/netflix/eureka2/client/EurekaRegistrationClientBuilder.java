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

package com.netflix.eureka2.client;

import com.netflix.eureka2.channel.RegistrationChannel;
import com.netflix.eureka2.client.channel.ClientChannelFactory;
import com.netflix.eureka2.client.channel.RegistrationChannelFactory;
import com.netflix.eureka2.client.registration.EurekaRegistrationClientImpl;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.client.resolver.ServerResolvers;

/**
 * @author David Liu
 */
public class EurekaRegistrationClientBuilder
        extends AbstractClientBuilder<EurekaRegistrationClient, EurekaRegistrationClientBuilder> {

    protected ServerResolver writeServerResolver;

    /**
     * Connect to write servers specified by the given write server resolver.
     *
     * @param writeServerResolver the resolver to specify which write server to connect to (may have redirects)
     * @return a builder to continue client construction
     */
    public EurekaRegistrationClientBuilder fromWriteServerResolver(ServerResolver writeServerResolver) {
        this.writeServerResolver = writeServerResolver;
        return self();
    }

    /**
     * Connect to a specific write server specified by the given hostname and registrationPort
     *
     * @param hostname the hostname for the write server to connect to.
     * @param registrationPort the registration port for the write server
     * @return a builder to continue client construction
     */
    public EurekaRegistrationClientBuilder fromHostname(String hostname, int registrationPort) {
        this.writeServerResolver = ServerResolvers.just(hostname, registrationPort);
        return self();
    }

    /**
     * Resolve target write servers via the dnsName provided, and then connect to the resolved write servers
     * on the provided registrationPort.
     *
     * @param dnsName a dns name from which to resolve write server hostnames for connection
     * @param registrationPort the registration port for the write servers
     * @return a builder to continue client construction
     */
    public EurekaRegistrationClientBuilder fromDns(String dnsName, int registrationPort) {
        this.writeServerResolver = ServerResolvers.forDnsName(dnsName, registrationPort);
        return self();
    }

    @Override
    protected EurekaRegistrationClient buildClient() {
        if (writeServerResolver == null) {
            throw new IllegalArgumentException("Cannot build client for registration without write server resolver");
        }

        ClientChannelFactory<RegistrationChannel> channelFactory
                = new RegistrationChannelFactory(transportConfig, writeServerResolver, clientMetricFactory);

        return new EurekaRegistrationClientImpl(channelFactory);
    }
}
