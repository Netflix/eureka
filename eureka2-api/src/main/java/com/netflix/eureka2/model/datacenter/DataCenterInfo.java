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

package com.netflix.eureka2.model.datacenter;

import java.util.List;

import com.netflix.eureka2.model.instance.NetworkAddress;

/**
 * {@link DataCenterInfo} encapsulates information about the data center where a given
 * server is running, plus server specific information, like IP addresses, host names, etc.
 *
 * Because for certain datacenters there are multiple network interfaces per server,
 * it is important to choose optimal interfaces for a pair of servers (private for collocated servers,
 * public if in different regions, etc). To support this process in a transparent way
 * eureka-client API provides peer address resolver abstractions.
 *
 * @author David Liu
 */
public interface DataCenterInfo {

    String getName();

    List<NetworkAddress> getAddresses();

    NetworkAddress getDefaultAddress();
}
