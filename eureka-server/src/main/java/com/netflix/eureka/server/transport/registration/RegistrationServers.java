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

package com.netflix.eureka.server.transport.registration;

import com.netflix.eureka.server.transport.TransportServer;
import com.netflix.eureka.server.transport.registration.protocol.asynchronous.AsyncRegistrationServer;
import com.netflix.eureka.transport.EurekaTransports;

/**
 * @author Tomasz Bak
 */
public class RegistrationServers {

    public static TransportServer tcpRegistrationServer(int port, RegistrationHandler handler) {
        return new AsyncRegistrationServer(
                EurekaTransports.tcpRegistrationServer(port),
                handler
        );
    }

    public static TransportServer httpRegistrationServer(int port, RegistrationHandler handler) {
        throw new RuntimeException("not implemented");
    }

    public static TransportServer websocketRegistrationServer(int port, RegistrationHandler handler) {
        throw new RuntimeException("not implemented");
    }
}
