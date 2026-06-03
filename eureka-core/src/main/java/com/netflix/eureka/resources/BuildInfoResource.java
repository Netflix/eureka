/*
 * Copyright 2012 Netflix, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.netflix.eureka.resources;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import com.netflix.eureka.EurekaServerContext;
import com.netflix.eureka.EurekaServerContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A resource for exposing build and runtime metadata about the Eureka server.
 *
 * @author Eureka Team
 */
@Path("/build-info")
@Produces("application/json")
public class BuildInfoResource {
    private static final Logger logger = LoggerFactory.getLogger(BuildInfoResource.class);
    private static final String UNKNOWN_VERSION = "unknown";

    private final EurekaServerContext serverContext;

    @Inject
    BuildInfoResource(EurekaServerContext serverContext) {
        this.serverContext = serverContext;
    }

    public BuildInfoResource() {
        this(EurekaServerContextHolder.getInstance().getServerContext());
    }

    @GET
    public Map<String, Object> getBuildInfo() {
        Map<String, Object> buildInfo = new HashMap<>();
        
        // Build version from MANIFEST.MF
        String buildVersion = getBuildVersion();
        buildInfo.put("buildVersion", buildVersion);
        
        // Java version
        String javaVersion = System.getProperty("java.version");
        buildInfo.put("javaVersion", javaVersion);
        
        // JVM uptime in seconds
        long uptimeMillis = ManagementFactory.getRuntimeMXBean().getUptime();
        long uptimeSeconds = uptimeMillis / 1000;
        buildInfo.put("uptimeSeconds", uptimeSeconds);
        
        // Server time in ISO-8601 format
        String serverTime = Instant.now().toString();
        buildInfo.put("serverTime", serverTime);
        
        return buildInfo;
    }

    private String getBuildVersion() {
        try {
            Package pkg = getClass().getPackage();
            String version = pkg.getImplementationVersion();
            if (version != null && !version.isEmpty()) {
                return version;
            }
        } catch (Exception e) {
            logger.debug("Failed to read build version from manifest", e);
        }
        return UNKNOWN_VERSION;
    }
}
