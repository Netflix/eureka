/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.eureka2.spi.transport;

import com.netflix.eureka2.internal.util.ExtLoader;
import com.netflix.eureka2.model.Server;
import com.netflix.eureka2.spi.channel.InterestHandler;
import com.netflix.eureka2.spi.channel.RegistrationHandler;
import com.netflix.eureka2.spi.channel.ReplicationHandler;

/**
 */
public abstract class EurekaClientTransportFactory {

    private static volatile EurekaClientTransportFactory defaultFactory;

    public abstract RegistrationHandler newRegistrationClientTransport(Server eurekaServer);

    public abstract InterestHandler newInterestTransport(Server eurekaServer);

    public abstract ReplicationHandler newReplicationTransport(Server eurekaServer);

    public static EurekaClientTransportFactory getDefaultFactory() {
        if(defaultFactory == null) {
            return ExtLoader.resolveDefaultClientTransportFactory();
        }
        return defaultFactory;
    }

    public static EurekaClientTransportFactory setDefaultFactory(EurekaClientTransportFactory newFactory) {
        EurekaClientTransportFactory previous = defaultFactory;
        defaultFactory = newFactory;
        return previous;
    }
}
