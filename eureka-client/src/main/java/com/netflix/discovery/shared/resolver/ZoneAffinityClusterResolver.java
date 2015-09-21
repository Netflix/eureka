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

package com.netflix.discovery.shared.resolver;

import java.util.List;

/**
 * It is a cluster resolver that reorders the server list, such that the first server on the list
 * is in the same zone as the client. The server is chosen randomly from the available pool of server in
 * that zone. The remaining servers are appended in a random order, local zone first, followed by servers from other zones.
 *
 * @author Tomasz Bak
 */
public class ZoneAffinityClusterResolver implements ClusterResolver {

    private final List<EurekaEndpoint> eurekaEndpoints;

    public ZoneAffinityClusterResolver(ClusterResolver delegate, String myZone) {
        List<EurekaEndpoint>[] parts = ResolverUtils.splitByZone(delegate.getClusterServers(), myZone);
        List<EurekaEndpoint> myZoneEndpoints = parts[0];
        List<EurekaEndpoint> remainingEndpoints = parts[1];
        this.eurekaEndpoints = randomizeAndMerge(myZoneEndpoints, remainingEndpoints);
    }

    @Override
    public List<EurekaEndpoint> getClusterServers() {
        return eurekaEndpoints;
    }

    @Override
    public void shutdown() {
    }

    private static List<EurekaEndpoint> randomizeAndMerge(List<EurekaEndpoint> myZoneEndpoints, List<EurekaEndpoint> remainingEndpoints) {
        if (myZoneEndpoints.isEmpty()) {
            return ResolverUtils.randomize(remainingEndpoints);
        }
        if (remainingEndpoints.isEmpty()) {
            return ResolverUtils.randomize(myZoneEndpoints);
        }
        List<EurekaEndpoint> mergedList = ResolverUtils.randomize(myZoneEndpoints);
        mergedList.addAll(ResolverUtils.randomize(remainingEndpoints));
        return mergedList;
    }
}
