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

package com.netflix.rx.eureka.client;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.net.InetSocketAddress;

import com.netflix.rx.eureka.client.metric.EurekaClientMetricFactory;
import com.netflix.rx.eureka.client.service.EurekaClientRegistry;
import com.netflix.rx.eureka.client.transport.TransportClient;
import com.netflix.rx.eureka.client.transport.TransportClients;
import com.netflix.rx.eureka.interests.ChangeNotification;
import com.netflix.rx.eureka.interests.Interest;
import com.netflix.rx.eureka.interests.Interests;
import com.netflix.rx.eureka.registry.EurekaRegistry;
import com.netflix.rx.eureka.registry.InstanceInfo;
import com.netflix.rx.eureka.transport.EurekaTransports.Codec;
import rx.Observable;

/**
 * @author Tomasz Bak
 */
@Singleton
public class EurekaClientImpl extends EurekaClient {

    private final EurekaRegistry<InstanceInfo> registry;
    private final RegistrationHandler registrationHandler;

    public EurekaClientImpl(EurekaRegistry<InstanceInfo> registry,
                            RegistrationHandler registrationHandler) {
        this.registry = registry;
        this.registrationHandler = registrationHandler;
    }

    public EurekaClientImpl(TransportClient writeClient, TransportClient readClient, EurekaClientMetricFactory metricFactory) {
        this(
                readClient == null ? null : new EurekaClientRegistry(readClient, metricFactory),
                writeClient == null ? null : new RegistrationHandlerImpl(writeClient, metricFactory)
        );
    }

    @Inject
    public EurekaClientImpl(@Named(READ_SERVER_RESOLVER_NAME) ServerResolver<InetSocketAddress> readServerResolver,
                            @Named(WRITE_SERVER_RESOLVER_NAME) ServerResolver<InetSocketAddress> writeServerResolver,
                            EurekaClientMetricFactory metricFactory) {
        //TODO: Default to avro as we are always going to use avro by default. Today it expects avro schema in CP.
        this(readServerResolver, writeServerResolver, Codec.Avro, metricFactory);
    }

    public EurekaClientImpl(ServerResolver<InetSocketAddress> readServerResolver,
                            ServerResolver<InetSocketAddress> writeServerResolver,
                            Codec codec,
                            EurekaClientMetricFactory metricFactory) {
        this(TransportClients.newTcpRegistrationClient(writeServerResolver, codec), TransportClients.newTcpDiscoveryClient(readServerResolver, codec), metricFactory);
    }

    @Override
    public Observable<Void> register(InstanceInfo instanceInfo) {
        return registrationHandler.register(instanceInfo);
    }

    @Override
    public Observable<Void> update(InstanceInfo instanceInfo) {
        return registrationHandler.update(instanceInfo);
    }

    @Override
    public Observable<Void> unregister(InstanceInfo instanceInfo) {
        return registrationHandler.unregister(instanceInfo);
    }

    @Override
    public Observable<ChangeNotification<InstanceInfo>> forInterest(final Interest<InstanceInfo> interest) {
        return registry.forInterest(interest);
    }

    @Override
    public Observable<ChangeNotification<InstanceInfo>> forApplication(String appName) {
        return forInterest(Interests.forApplications(appName));
    }

    @Override
    public Observable<ChangeNotification<InstanceInfo>> forVips(String... vips) {
        return forInterest(Interests.forVips(vips));
    }

    @Override
    public void close() {
        registry.shutdown();
        if (null != registrationHandler) {
            registrationHandler.shutdown();
        }
    }

    @Override
    public String toString() {
        return registry.toString();
    }
}