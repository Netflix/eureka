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

import com.netflix.eureka2.server.service.InterestChannelMetrics;
import com.netflix.eureka2.server.service.RegistrationChannelMetrics;
import com.netflix.eureka2.server.service.ReplicationChannelMetrics;
import com.netflix.eureka2.transport.base.MessageConnectionMetrics;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

/**
 * @author Tomasz Bak
 */
@Singleton
public class EurekaServerMetricFactory {

    private static EurekaServerMetricFactory INSTANCE;

    private final MessageConnectionMetrics registrationConnectionMetrics;
    private final MessageConnectionMetrics replicationConnectionMetrics;
    private final MessageConnectionMetrics discoveryConnectionMetrics;
    private final RegistrationChannelMetrics registrationChannelMetrics;
    private final ReplicationChannelMetrics replicationChannelMetrics;
    private final InterestChannelMetrics interestChannelMetrics;

    @Inject
    public EurekaServerMetricFactory(
            @Named("registration") MessageConnectionMetrics registrationConnectionMetrics,
            @Named("replication") MessageConnectionMetrics replicationConnectionMetrics,
            @Named("discovery") MessageConnectionMetrics discoveryConnectionMetrics,
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

    public MessageConnectionMetrics getRegistrationConnectionMetrics() {
        return registrationConnectionMetrics;
    }

    public MessageConnectionMetrics getReplicationConnectionMetrics() {
        return replicationConnectionMetrics;
    }

    public MessageConnectionMetrics getDiscoveryConnectionMetrics() {
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
                        new MessageConnectionMetrics("registration"),
                        new MessageConnectionMetrics("replication"),
                        new MessageConnectionMetrics("discovery"),
                        new RegistrationChannelMetrics(),
                        new ReplicationChannelMetrics(),
                        new InterestChannelMetrics()
                );
            }
        }
        return INSTANCE;
    }
}
