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

package com.netflix.rx.eureka.registry;

import java.util.HashSet;

import com.netflix.rx.eureka.utils.Sets;

/**
 * @author Tomasz Bak
 */
public enum SampleServicePort {

    HttpPort() {
        @Override
        public ServicePort build() {
            return new ServicePort("WebServer", 80, false);
        }
    },
    HttpsPort() {
        @Override
        public ServicePort build() {
            return new ServicePort("WebServer", 443, true);
        }
    };

    public abstract ServicePort build();

    public static HashSet<ServicePort> httpPorts() {
        return Sets.asSet(HttpPort.build(), HttpsPort.build());
    }
}
