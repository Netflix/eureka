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

package com.netflix.eureka2.server.channel;

import com.netflix.eureka2.channel.InterestChannel;

/**
 * Server side interest channel factory.
 *
 * A channel instance is dedicated to a client connection and is stateful with respect to the operations that can be
 * performed on a service instance.
 *
 * @author Tomasz Bak
 */
public interface InterestChannelFactory {
    /**
     * Returns a new {@link com.netflix.eureka2.channel.InterestChannel}.
     *
     * @return A new {@link com.netflix.eureka2.channel.InterestChannel}.
     */
    InterestChannel newInterestChannel();
}
