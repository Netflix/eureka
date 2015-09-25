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

import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * It is a cluster resolver that reorders the server list, such that the first server on the list
 * is in the same zone as the client. The server is chosen randomly from the available pool of server in
 * that zone. The remaining servers are appended in a random order, local zone first, followed by servers from other zones.
 *
 * @author Tomasz Bak
 */
public class ZoneAffinityClusterResolver implements ClusterResolver {

    private static final Logger logger = LoggerFactory.getLogger(ZoneAffinityClusterResolver.class);

    private final List<EurekaEndpoint> eurekaEndpoints;
    private final String region;

    /**
     * A zoneAffinity defines zone affinity (true) or anti-affinity rules (false).
     */
    public ZoneAffinityClusterResolver(ClusterResolver delegate, String myZone, boolean zoneAffinity) {
        this.region = delegate.getRegion();
        List<EurekaEndpoint>[] parts = ResolverUtils.splitByZone(delegate.getClusterEndpoints(), myZone);
        List<EurekaEndpoint> myZoneEndpoints = parts[0];
        List<EurekaEndpoint> remainingEndpoints = parts[1];
        List<EurekaEndpoint> randomizedList = randomizeAndMerge(myZoneEndpoints, remainingEndpoints);
        if (!zoneAffinity) {
            Collections.reverse(randomizedList);
        }
        this.eurekaEndpoints = randomizedList;

        if (logger.isDebugEnabled()) {
            logger.debug("Local zone={}; resolved to: {}", myZone, eurekaEndpoints);
        }
    }

    @Override
    public String getRegion() {
        return region;
    }

    @Override
    public List<EurekaEndpoint> getClusterEndpoints() {
        return eurekaEndpoints;
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
