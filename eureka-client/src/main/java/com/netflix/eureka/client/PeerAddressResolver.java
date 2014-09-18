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

package com.netflix.eureka.client;

import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.registry.NetworkAddress;

/**
 * Most datacenters run servers with multiple IP addresses, and protocol families.
 * The choice of a particular IP address and IP protocol version will depend on the relative
 * location of two peer machines, and possibly other factors. This interface provides abstraction
 * over such resolution process.
 *
 * @author Tomasz Bak
 */
public interface PeerAddressResolver {

    NetworkAddress optimalRouting(InstanceInfo current, InstanceInfo target);

}
