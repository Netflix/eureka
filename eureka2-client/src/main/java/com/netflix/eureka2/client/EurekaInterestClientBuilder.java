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
import com.netflix.eureka2.client.interest.EurekaInterestClientImpl;
import com.netflix.eureka2.registry.EurekaRegistry;
import com.netflix.eureka2.registry.EurekaRegistryImpl;
import com.netflix.eureka2.registry.instance.InstanceInfo;

/**
 * @author David Liu
 */
public class EurekaInterestClientBuilder
        extends AbstractClientBuilder<EurekaInterestClient, EurekaInterestClientBuilder> {

    private static final String INTEREST_CLIENT_ID = "interestClient";

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
        if(clientId == null) {
            clientId = INTEREST_CLIENT_ID;
        }

        EurekaRegistry<InstanceInfo> registry = new EurekaRegistryImpl(registryMetricFactory);

        ClientChannelFactory<InterestChannel> channelFactory
                = new InterestChannelFactory(clientId, transportConfig, serverResolver, registry, clientMetricFactory);

        return new EurekaInterestClientImpl(registry, channelFactory);
    }
}
