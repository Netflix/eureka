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

package com.netflix.eureka2.server.metric;

import javax.inject.Inject;
import javax.inject.Named;

import com.netflix.eureka2.metric.MessageConnectionMetrics;

/**
 * @author Tomasz Bak
 */
public class WriteServerMetricFactory extends EurekaServerMetricFactory {
    private static WriteServerMetricFactory INSTANCE;
    private final MessageConnectionMetrics registrationServerConnectionMetrics;
    private final MessageConnectionMetrics discoveryServerConnectionMetrics;
    private final MessageConnectionMetrics replicationServerConnectionMetrics;

    private final RegistrationChannelMetrics registrationChannelMetrics;
    private final ReplicationChannelMetrics replicationChannelMetrics;

    private final EurekaServerRegistryMetrics eurekaServerRegistryMetrics;
    private final EvictionQueueMetrics evictionQueueMetrics;

    @Inject
    public WriteServerMetricFactory(
            @Named("registration") MessageConnectionMetrics registrationConnectionMetrics,
            @Named("replication") MessageConnectionMetrics replicationConnectionMetrics,
            @Named("discovery") MessageConnectionMetrics discoveryConnectionMetrics,
            @Named("clientRegistration") MessageConnectionMetrics registrationServerConnectionMetrics,
            @Named("clientDiscovery") MessageConnectionMetrics discoveryServerConnectionMetrics,
            @Named("clientReplication") MessageConnectionMetrics replicationServerConnectionMetrics,
            RegistrationChannelMetrics registrationChannelMetrics,
            ReplicationChannelMetrics replicationChannelMetrics,
            InterestChannelMetrics interestChannelMetrics,
            EurekaServerRegistryMetrics eurekaServerRegistryMetrics,
            EvictionQueueMetrics evictionQueueMetrics) {
        super(registrationConnectionMetrics, replicationConnectionMetrics, discoveryConnectionMetrics,
                interestChannelMetrics
        );
        this.registrationServerConnectionMetrics = registrationServerConnectionMetrics;
        this.discoveryServerConnectionMetrics = discoveryServerConnectionMetrics;
        this.replicationServerConnectionMetrics = replicationServerConnectionMetrics;
        this.registrationChannelMetrics = registrationChannelMetrics;
        this.replicationChannelMetrics = replicationChannelMetrics;
        this.eurekaServerRegistryMetrics = eurekaServerRegistryMetrics;
        this.evictionQueueMetrics = evictionQueueMetrics;
    }

    public MessageConnectionMetrics getReplicationServerConnectionMetrics() {
        return replicationServerConnectionMetrics;
    }

    public MessageConnectionMetrics getRegistrationServerConnectionMetrics() {
        return registrationServerConnectionMetrics;
    }

    public MessageConnectionMetrics getDiscoveryServerConnectionMetrics() {
        return discoveryServerConnectionMetrics;
    }

    public RegistrationChannelMetrics getRegistrationChannelMetrics() {
        return registrationChannelMetrics;
    }

    public ReplicationChannelMetrics getReplicationChannelMetrics() {
        return replicationChannelMetrics;
    }

    public EurekaServerRegistryMetrics getEurekaServerRegistryMetrics() {
        return eurekaServerRegistryMetrics;
    }

    public EvictionQueueMetrics getEvictionQueueMetrics() {
        return evictionQueueMetrics;
    }

    public static WriteServerMetricFactory writeServerMetrics() {
        if (INSTANCE == null) {
            synchronized (WriteServerMetricFactory.class) {

                MessageConnectionMetrics clientRegistration = new MessageConnectionMetrics("clientRegistration");
                clientRegistration.bindMetrics();

                MessageConnectionMetrics clientDiscovery = new MessageConnectionMetrics("clientDiscovery");
                clientDiscovery.bindMetrics();

                MessageConnectionMetrics clientReplication = new MessageConnectionMetrics("clientReplication");
                clientReplication.bindMetrics();

                RegistrationChannelMetrics registrationChannelMetrics = new RegistrationChannelMetrics();
                registrationChannelMetrics.bindMetrics();

                ReplicationChannelMetrics replicationChannelMetrics = new ReplicationChannelMetrics();
                replicationChannelMetrics.bindMetrics();

                EurekaServerRegistryMetrics eurekaServerRegistryMetrics = new EurekaServerRegistryMetrics();
                eurekaServerRegistryMetrics.bindMetrics();

                EvictionQueueMetrics evictionQueueMetrics = new EvictionQueueMetrics();
                evictionQueueMetrics.bindMetrics();

                INSTANCE = new WriteServerMetricFactory(
                        serverMetrics().getRegistrationConnectionMetrics(),
                        serverMetrics().getReplicationConnectionMetrics(),
                        serverMetrics().getDiscoveryConnectionMetrics(),
                        clientRegistration,
                        clientDiscovery,
                        clientReplication,
                        registrationChannelMetrics,
                        replicationChannelMetrics,
                        serverMetrics().getInterestChannelMetrics(),
                        eurekaServerRegistryMetrics,
                        evictionQueueMetrics
                );
            }
        }
        return INSTANCE;
    }
}
