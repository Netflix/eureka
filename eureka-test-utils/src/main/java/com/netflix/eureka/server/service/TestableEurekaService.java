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

package com.netflix.eureka.server.service;

import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.service.InterestChannel;
import com.netflix.eureka.service.RegistrationChannel;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Stub implementation of {@link EurekaServerService} and related channels for
 * testing purposes.
 *
 * @author Tomasz Bak
 */
public class TestableEurekaService implements EurekaServerService {

    private final BlockingQueue<RegistrationChannel> registrationChannels = new LinkedBlockingQueue<RegistrationChannel>();

    private final BlockingQueue<InterestChannel> interestChannels = new LinkedBlockingQueue<InterestChannel>();

    @Override
    public ReplicationChannel newReplicationChannel(InstanceInfo sourceServer) {
        return null;
    }

    @Override
    public InterestChannel newInterestChannel() {
        TestableInterestChannel interestChannel = new TestableInterestChannel();
        interestChannels.add(interestChannel);
        return interestChannel;
    }

    @Override
    public RegistrationChannel newRegistrationChannel() {
        TestableRegistrationChannel registrationChannel = new TestableRegistrationChannel();
        registrationChannels.add(registrationChannel);
        return registrationChannel;
    }

    @Override
    public void shutdown() {
        // No Op
    }

    public BlockingQueue<RegistrationChannel> viewNewRegistrationChannels() {
        return registrationChannels;
    }

    public BlockingQueue<InterestChannel> viewNewInterestChannels() {
        return interestChannels;
    }

}
