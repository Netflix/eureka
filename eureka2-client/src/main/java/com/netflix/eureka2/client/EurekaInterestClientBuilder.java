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

import com.netflix.eureka2.channel.InterestChannel;
import com.netflix.eureka2.client.channel.ClientChannelFactory;
import com.netflix.eureka2.client.channel.InterestChannelFactory;
import com.netflix.eureka2.client.interest.BatchAwareIndexRegistry;
import com.netflix.eureka2.client.interest.BatchingRegistry;
import com.netflix.eureka2.client.interest.BatchingRegistryImpl;
import com.netflix.eureka2.client.interest.EurekaInterestClientImpl;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.client.resolver.ServerResolvers;
import com.netflix.eureka2.interests.IndexRegistryImpl;
import com.netflix.eureka2.registry.PreservableEurekaRegistry;
import com.netflix.eureka2.registry.SourcedEurekaRegistryImpl;
import com.netflix.eureka2.registry.instance.InstanceInfo;

/**
 * @author David Liu
 */
public class EurekaInterestClientBuilder
        extends AbstractClientBuilder<EurekaInterestClient, EurekaInterestClientBuilder> {

    protected ServerResolver readServerResolver;

    /**
     * Connect to read servers specified by the given read server resolver.
     *
     * @param readServerResolver the resolver to specify which read server to connect to (may have redirects)
     * @return a builder to continue client construction
     */
    public EurekaInterestClientBuilder fromReadServerResolver(ServerResolver readServerResolver) {
        this.readServerResolver = readServerResolver;
        return self();
    }

    /**
     * Connect to a specific read server specified by the given hostname and registrationPort
     *
     * @param hostname the hostname for the read server to connect to.
     * @param interestPort the registration port for the read server
     * @return a builder to continue client construction
     */
    public EurekaInterestClientBuilder fromHostname(String hostname, int interestPort) {
        this.readServerResolver = ServerResolvers.just(hostname, interestPort);
        return self();
    }

    /**
     * Resolve target read servers via the dnsName provided, and then connect to the resolved read servers
     * on the provided interestPort.
     *
     * @param dnsName a dns name from which to resolve read server hostnames for connection
     * @param interestPort the registration port for the read servers
     * @return a builder to continue client construction
     */
    public EurekaInterestClientBuilder fromDns(String dnsName, int interestPort) {
        this.readServerResolver = ServerResolvers.forDnsName(dnsName, interestPort);
        return self();
    }

    /**
     * Resolve target read servers by first connecting to a write server with the interest protocol to pre-discover
     * available read servers hostnames and supported interestPorts. The initial write server for pre-discovery is
     * resolved from dnsName.
     *
     * @param writeDnsName a dns name from which to resolve write server hostnames for connection
     * @param writeInterestPort the interest port for the *write* servers to pre-discover read servers
     * @param readServerVip vip address for read server identification from the write server registry
     * @return a builder to continue client construction
     */
    public EurekaInterestClientBuilder fromWriteInterestResolver(ServerResolver writeInterestResolver, String readServerVip) {
        this.readServerResolver = ServerResolvers.fromWriteServer(writeInterestResolver, readServerVip);
        return self();
    }

    /**
     * Resolve target read servers by first connecting to a write server with the interest protocol to pre-discover
     * available read servers hostnames and supported interestPorts. The initial write server for pre-discovery is
     * resolved from dnsName.
     *
     * @param writeDnsName a dns name from which to resolve write server hostnames for connection
     * @param writeInterestPort the interest port for the *write* servers to pre-discover read servers
     * @param readServerVip vip address for read server identification from the write server registry
     * @return a builder to continue client construction
     */
    public EurekaInterestClientBuilder fromWriteDns(String writeDnsName, int writeInterestPort, String readServerVip) {
        ServerResolver writeInterestResolver = ServerResolvers.forDnsName(writeDnsName, writeInterestPort);
        this.readServerResolver = ServerResolvers.fromWriteServer(writeInterestResolver, readServerVip);
        return self();
    }

    @Override
    protected EurekaInterestClient buildClient() {
        if (readServerResolver == null) {
            throw new IllegalArgumentException("Cannot build client for discovery without read server resolver");
        }

        BatchingRegistry<InstanceInfo> remoteBatchingRegistry = new BatchingRegistryImpl<>();
        BatchAwareIndexRegistry<InstanceInfo> indexRegistry = new BatchAwareIndexRegistry<>(
                new IndexRegistryImpl<InstanceInfo>(), remoteBatchingRegistry);

        PreservableEurekaRegistry registry = new PreservableEurekaRegistry(
                new SourcedEurekaRegistryImpl(indexRegistry, registryMetricFactory),
                registryConfig,
                registryMetricFactory);

        ClientChannelFactory<InterestChannel> channelFactory
                = new InterestChannelFactory(transportConfig, readServerResolver, registry, remoteBatchingRegistry, clientMetricFactory);

        return new EurekaInterestClientImpl(registry, channelFactory);
    }
}
