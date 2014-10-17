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
import javax.inject.Singleton;

import com.netflix.rx.eureka.server.service.InterestChannelMetrics;
import com.netflix.rx.eureka.server.service.RegistrationChannelMetrics;
import com.netflix.rx.eureka.server.service.ReplicationChannelMetrics;
import com.netflix.rx.eureka.server.transport.ClientConnectionMetrics;

/**
 * @author Tomasz Bak
 */
@Singleton
public class EurekaServerMetricFactory {

    private static EurekaServerMetricFactory INSTANCE;

    private final ClientConnectionMetrics registrationConnectionMetrics;
    private final ClientConnectionMetrics replicationConnectionMetrics;
    private final ClientConnectionMetrics discoveryConnectionMetrics;
    private final RegistrationChannelMetrics registrationChannelMetrics;
    private final ReplicationChannelMetrics replicationChannelMetrics;
    private final InterestChannelMetrics interestChannelMetrics;

    @Inject
    public EurekaServerMetricFactory(
            @Named("registration") ClientConnectionMetrics registrationConnectionMetrics,
            @Named("replication") ClientConnectionMetrics replicationConnectionMetrics,
            @Named("discovery") ClientConnectionMetrics discoveryConnectionMetrics,
            RegistrationChannelMetrics registrationChannelMetrics,
            ReplicationChannelMetrics replicationChannelMetrics,
            InterestChannelMetrics interestChannelMetrics) {
        this.registrationConnectionMetrics = registrationConnectionMetrics;
        this.replicationConnectionMetrics = replicationConnectionMetrics;
        this.discoveryConnectionMetrics = discoveryConnectionMetrics;
        this.registrationChannelMetrics = registrationChannelMetrics;
        this.replicationChannelMetrics = replicationChannelMetrics;
        this.interestChannelMetrics = interestChannelMetrics;
    }

    public ClientConnectionMetrics getRegistrationConnectionMetrics() {
        return registrationConnectionMetrics;
    }

    public ClientConnectionMetrics getReplicationConnectionMetrics() {
        return replicationConnectionMetrics;
    }

    public ClientConnectionMetrics getDiscoveryConnectionMetrics() {
        return discoveryConnectionMetrics;
    }

    public RegistrationChannelMetrics getRegistrationChannelMetrics() {
        return registrationChannelMetrics;
    }

    public ReplicationChannelMetrics getReplicationChannelMetrics() {
        return replicationChannelMetrics;
    }

    public InterestChannelMetrics getInterestChannelMetrics() {
        return interestChannelMetrics;
    }

    public static EurekaServerMetricFactory serverMetrics() {
        if (INSTANCE == null) {
            synchronized (EurekaServerMetricFactory.class) {
                INSTANCE = new EurekaServerMetricFactory(
                        new ClientConnectionMetrics("eureka2.registration.connection"),
                        new ClientConnectionMetrics("eureka2.replication.connection"),
                        new ClientConnectionMetrics("eureka2.discovery.connection"),
                        new RegistrationChannelMetrics("eureka2.registration.channel"),
                        new ReplicationChannelMetrics("eureka2.replication.channel"),
                        new InterestChannelMetrics("eureka2.discovery.channel")
                );
            }
        }
        return INSTANCE;
    }
}
