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

package com.netflix.eureka.client.service;

import com.netflix.eureka.client.transport.TransportClientProvider;
import com.netflix.eureka.client.transport.registration.RegistrationClient;
import com.netflix.eureka.service.RegistrationChannel;

/**
 * @author Tomasz Bak
 */
public class RegistrationChannelMonitor extends ServiceChannelMonitor<RegistrationClient, RegistrationChannel> {
    protected RegistrationChannelMonitor(TransportClientProvider<RegistrationClient> transportClientProvider) {
        super(transportClientProvider);
    }

    @Override
    protected RegistrationChannel createChannel(RegistrationClient client) {
        return new RegistrationChannelImpl(client);
    }

    @Override
    protected RegistrationChannel createUnavailableChannel() {
        return UnavailableRegistrationChannel.INSTANCE;
    }
}
