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

package com.netflix.eureka.client.inject;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import java.net.InetSocketAddress;

import com.netflix.eureka.client.EurekaClient;
import com.netflix.eureka.client.EurekaClientImpl;
import com.netflix.eureka.client.ServerResolver;
import com.netflix.eureka.client.service.EurekaClientService;
import com.netflix.eureka.client.service.EurekaServiceImpl;
import com.netflix.eureka.client.transport.TransportClient;
import com.netflix.eureka.client.transport.TransportClients;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.registry.LeasedInstanceRegistry;
import com.netflix.eureka.transport.EurekaTransports.Codec;

/**
 * Provider of {@link EurekaClient} instances with a few injectable dependencies.
 *
 * @author Tomasz Bak
 */
public class EurekaClientProvider implements Provider<EurekaClient> {

    public static final String LOCAL_INSTANCE_INFO_TAG = "localInstanceInfo";

    private final ServerResolver<InetSocketAddress> serverResolver;
    private final InstanceInfo localInstance;
    private final Codec codec;

    @Inject
    public EurekaClientProvider(ServerResolver<InetSocketAddress> serverResolver,
                                @Named(LOCAL_INSTANCE_INFO_TAG) InstanceInfo localInstance,
                                Codec codec) {
        this.serverResolver = serverResolver;
        this.localInstance = localInstance;
        this.codec = codec;
    }

    @Override
    public EurekaClient get() {
        TransportClient transportClient = TransportClients.newTcpDiscoveryClient(serverResolver, codec);
        EurekaClientService eurekaClientService = EurekaServiceImpl.forReadServer(
                new LeasedInstanceRegistry(localInstance),
                transportClient
        );
        return new EurekaClientImpl(eurekaClientService);
    }
}
