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

package com.netflix.eureka2.testkit.data.builder;

import com.netflix.eureka2.Names;
import com.netflix.eureka2.model.InstanceModel;
import com.netflix.eureka2.model.instance.ServicePort;
import com.netflix.eureka2.spi.transport.EurekaTransportFactory;
import com.netflix.eureka2.utils.ExtCollections;

import java.util.HashSet;

/**
 * @author Tomasz Bak
 */
public enum SampleServicePort {

    HttpPort() {
        @Override
        public ServicePort build() {
            return InstanceModel.getDefaultModel().newServicePort("WebServer", 80, false);
        }
    },
    HttpsPort() {
        @Override
        public ServicePort build() {
            return InstanceModel.getDefaultModel().newServicePort("WebServer", 443, true);
        }
    },
    EurekaRegistrationPort() {
        @Override
        public ServicePort build() {
            return InstanceModel.getDefaultModel().newServicePort(Names.REGISTRATION, EurekaTransportFactory.DEFAULT_REGISTRATION_PORT, false);
        }
    },
    EurekaDiscoveryPort() {
        @Override
        public ServicePort build() {
            return InstanceModel.getDefaultModel().newServicePort(Names.INTEREST, EurekaTransportFactory.DEFAULT_DISCOVERY_PORT, false);
        }
    },
    EurekaReplicationPort() {
        @Override
        public ServicePort build() {
            return InstanceModel.getDefaultModel().newServicePort(Names.REPLICATION, EurekaTransportFactory.DEFAULT_REPLICATION_PORT, false);
        }
    };

    public abstract ServicePort build();

    public static HashSet<ServicePort> httpPorts() {
        return ExtCollections.asSet(HttpPort.build(), HttpsPort.build());
    }
}
