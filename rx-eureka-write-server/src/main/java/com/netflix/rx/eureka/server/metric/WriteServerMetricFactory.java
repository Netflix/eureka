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

package com.netflix.rx.eureka.server.metric;

import javax.inject.Inject;
import javax.inject.Named;

import com.netflix.rx.eureka.client.transport.ServerConnectionMetrics;
import com.netflix.rx.eureka.server.service.InterestChannelMetrics;
import com.netflix.rx.eureka.server.service.RegistrationChannelMetrics;
import com.netflix.rx.eureka.server.service.ReplicationChannelMetrics;
import com.netflix.rx.eureka.server.transport.ClientConnectionMetrics;

/**
 * @author Tomasz Bak
 */
public class WriteServerMetricFactory extends EurekaServerMetricFactory {
    private static WriteServerMetricFactory INSTANCE;
    private final ServerConnectionMetrics registrationServerConnectionMetrics;
    private final ServerConnectionMetrics discoveryServerConnectionMetrics;
    private final ServerConnectionMetrics replicationServerConnectionMetrics;

    @Inject
    public WriteServerMetricFactory(
            @Named("registration") ClientConnectionMetrics registrationConnectionMetrics,
            @Named("replication") ClientConnectionMetrics replicationConnectionMetrics,
            @Named("discovery") ClientConnectionMetrics discoveryConnectionMetrics,
            @Named("registration") ServerConnectionMetrics registrationServerConnectionMetrics,
            @Named("discovery") ServerConnectionMetrics discoveryServerConnectionMetrics,
            @Named("replication") ServerConnectionMetrics replicationServerConnectionMetrics,
            RegistrationChannelMetrics registrationChannelMetrics,
            ReplicationChannelMetrics replicationChannelMetrics,
            InterestChannelMetrics interestChannelMetrics) {
        super(registrationConnectionMetrics, replicationConnectionMetrics, discoveryConnectionMetrics,
                registrationChannelMetrics, replicationChannelMetrics, interestChannelMetrics);
        this.registrationServerConnectionMetrics = registrationServerConnectionMetrics;
        this.discoveryServerConnectionMetrics = discoveryServerConnectionMetrics;
        this.replicationServerConnectionMetrics = replicationServerConnectionMetrics;
    }

    public ServerConnectionMetrics getReplicationServerConnectionMetrics() {
        return replicationServerConnectionMetrics;
    }

    public ServerConnectionMetrics getRegistrationServerConnectionMetrics() {
        return registrationServerConnectionMetrics;
    }

    public ServerConnectionMetrics getDiscoveryServerConnectionMetrics() {
        return discoveryServerConnectionMetrics;
    }

    public static WriteServerMetricFactory writeServerMetrics() {
        if (INSTANCE == null) {
            synchronized (WriteServerMetricFactory.class) {
                INSTANCE = new WriteServerMetricFactory(
                        serverMetrics().getRegistrationConnectionMetrics(),
                        serverMetrics().getReplicationConnectionMetrics(),
                        serverMetrics().getDiscoveryConnectionMetrics(),
                        new ServerConnectionMetrics("eureka2.client.registration"),
                        new ServerConnectionMetrics("eureka2.client.discovery"),
                        new ServerConnectionMetrics("eureka2.replication.serverConnection"),
                        serverMetrics().getRegistrationChannelMetrics(),
                        serverMetrics().getReplicationChannelMetrics(),
                        serverMetrics().getInterestChannelMetrics()
                );
            }
        }
        return INSTANCE;
    }
}
