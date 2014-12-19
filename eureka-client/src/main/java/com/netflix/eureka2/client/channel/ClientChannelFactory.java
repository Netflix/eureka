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

package com.netflix.eureka2.client.channel;

import com.netflix.eureka2.channel.RegistrationChannel;

/**
 * Channels factory for Eureka client.
 *
 * @author Tomasz Bak
 */
public interface ClientChannelFactory {

    enum Mode {Read, Write, ReadWrite}

    /**
     * Returns a new {@link com.netflix.eureka2.channel.InterestChannel}.
     *
     * @return A new {@link com.netflix.eureka2.channel.InterestChannel}.
     */
    ClientInterestChannel newInterestChannel();

    /**
     * Returns a new {@link com.netflix.eureka2.channel.RegistrationChannel}.
     *
     * @return A new {@link com.netflix.eureka2.channel.RegistrationChannel}.
     */
    RegistrationChannel newRegistrationChannel();

    /**
     * Mode specifies constraints on channels that can be created.
     */
    Mode mode();

    /**
     * Release underlying resources.
     */
    void shutdown();
}
