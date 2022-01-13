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

import java.net.URL;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Statically configured Eureka server pool.
 *
 * @author Tomasz Bak
 */
public class StaticClusterResolver<T extends EurekaEndpoint> implements ClusterResolver<T> {

    private static final Logger logger = LoggerFactory.getLogger(StaticClusterResolver.class);

    private final List<T> eurekaEndpoints;
    private final String region;

    @SafeVarargs
    public StaticClusterResolver(String region, T... eurekaEndpoints) {
        this(region, Arrays.asList(eurekaEndpoints));
    }

    public StaticClusterResolver(String region, List<T> eurekaEndpoints) {
        this.eurekaEndpoints = eurekaEndpoints;
        this.region = region;
        logger.debug("Fixed resolver configuration: {}", eurekaEndpoints);
    }

    @Override
    public String getRegion() {
        return region;
    }

    @Override
    public List<T> getClusterEndpoints() {
        return eurekaEndpoints;
    }

    public static ClusterResolver<EurekaEndpoint> fromURL(String regionName, URL serviceUrl) {
        boolean isSecure = "https".equalsIgnoreCase(serviceUrl.getProtocol());
        int defaultPort = isSecure ? 443 : 80;
        int port = serviceUrl.getPort() == -1 ? defaultPort : serviceUrl.getPort();

        return new StaticClusterResolver<EurekaEndpoint>(
                regionName,
                new DefaultEndpoint(serviceUrl.getHost(), port, isSecure, serviceUrl.getPath())
        );
    }
}
