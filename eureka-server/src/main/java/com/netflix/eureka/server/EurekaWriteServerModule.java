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

package com.netflix.eureka.server;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import com.netflix.eureka.client.inject.EurekaClientProvider;
import com.netflix.eureka.registry.EurekaRegistry;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.registry.LeasedInstanceRegistry;
import com.netflix.eureka.transport.EurekaTransports.Codec;

/**
 * @author Tomasz Bak
 */
public class EurekaWriteServerModule extends AbstractModule {

    private final InstanceInfo localInstanceInfo;
    private final Codec codec;

    public EurekaWriteServerModule(InstanceInfo localInstanceInfo, Codec codec) {
        this.localInstanceInfo = localInstanceInfo;
        this.codec = codec;
    }

    @Override
    public void configure() {
        bind(InstanceInfo.class)
                .annotatedWith(Names.named(EurekaClientProvider.LOCAL_INSTANCE_INFO_TAG))
                .toInstance(localInstanceInfo);
        bind(Codec.class).toInstance(codec);

        bind(EurekaRegistry.class).toInstance(new LeasedInstanceRegistry(localInstanceInfo));
    }
}
