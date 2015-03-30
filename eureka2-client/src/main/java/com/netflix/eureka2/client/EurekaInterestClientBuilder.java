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
import com.netflix.eureka2.interests.IndexRegistryImpl;
import com.netflix.eureka2.registry.PreservableEurekaRegistry;
import com.netflix.eureka2.registry.SourcedEurekaRegistryImpl;
import com.netflix.eureka2.registry.instance.InstanceInfo;

/**
 * @author David Liu
 */
public class EurekaInterestClientBuilder
        extends AbstractClientBuilder<EurekaInterestClient, EurekaInterestClientBuilder> {

    /**
     * @deprecated do not create explicitly, use {@link Eurekas#newInterestClientBuilder()}
     * In future releases access right to this constructor may narrow (after rc.3)
     */
    @Deprecated
    public EurekaInterestClientBuilder() {
    }

    @Override
    protected EurekaInterestClient buildClient() {
        if (serverResolver == null) {
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
                = new InterestChannelFactory(transportConfig, serverResolver, registry, remoteBatchingRegistry, clientMetricFactory);

        return new EurekaInterestClientImpl(registry, channelFactory);
    }
}
