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

import com.netflix.eureka2.Names;
import com.netflix.eureka2.client.interest.EurekaInterestClientImpl2;
import com.netflix.eureka2.model.InstanceModel;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.registry.EurekaRegistry;
import com.netflix.eureka2.registry.EurekaRegistryImpl;
import rx.schedulers.Schedulers;

/**
 * @author David Liu
 */
public class EurekaInterestClientBuilder extends AbstractClientBuilder<EurekaInterestClient, EurekaInterestClientBuilder> {

    private static final long RETRY_INTERVAL_MS = 5 * 1000;

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
        if (clientId == null) {
            clientId = Names.INTEREST_CLIENT;
        }

        EurekaRegistry<InstanceInfo> registry = new EurekaRegistryImpl(registryMetricFactory);

        Source clientSource = InstanceModel.getDefaultModel().createSource(Source.Origin.LOCAL, clientId);

        return new EurekaInterestClientImpl2(clientSource, serverResolver, transportFactory, transportConfig, registry, RETRY_INTERVAL_MS, Schedulers.computation());
    }
}
