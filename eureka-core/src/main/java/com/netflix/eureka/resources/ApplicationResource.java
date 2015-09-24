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

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.netflix.appinfo.EurekaAccept;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.eureka.EurekaServerConfig;
import com.netflix.eureka.registry.PeerAwareInstanceRegistry;
import com.netflix.eureka.Version;
import com.netflix.eureka.cluster.PeerEurekaNode;
import com.netflix.eureka.registry.ResponseCache;
import com.netflix.eureka.registry.Key.KeyType;
import com.netflix.eureka.registry.Key;
import com.netflix.eureka.util.EurekaMonitors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A <em>jersey</em> resource that handles request related to a particular
 * {@link com.netflix.discovery.shared.Application}.
 *
 * @author Karthik Ranganathan, Greg Kim
 *
 */
@Produces({"application/xml", "application/json"})
public class ApplicationResource {
    private static final Logger logger = LoggerFactory.getLogger(ApplicationResource.class);

    private final String appName;
    private final EurekaServerConfig serverConfig;
    private final PeerAwareInstanceRegistry registry;
    private final ResponseCache responseCache;

    ApplicationResource(String appName,
                        EurekaServerConfig serverConfig,
                        PeerAwareInstanceRegistry registry) {
        this.appName = appName.toUpperCase();
        this.serverConfig = serverConfig;
        this.registry = registry;
        this.responseCache = registry.getResponseCache();
    }

    public String getAppName() {
        return appName;
    }

    /**
     * Gets information about a particular {@link com.netflix.discovery.shared.Application}.
     *
     * @param version
     *            the version of the request.
     * @param acceptHeader
     *            the accept header of the request to indicate whether to serve
     *            JSON or XML data.
     * @return the response containing information about a particular
     *         application.
     */
    @GET
    public Response getApplication(@PathParam("version") String version,
                                   @HeaderParam("Accept") final String acceptHeader,
                                   @HeaderParam(EurekaAccept.HTTP_X_EUREKA_ACCEPT) String eurekaAccept) {
        if (!registry.shouldAllowAccess(false)) {
            return Response.status(Status.FORBIDDEN).build();
        }

        EurekaMonitors.GET_APPLICATION.increment();

        CurrentRequestVersion.set(Version.toEnum(version));
        KeyType keyType = Key.KeyType.JSON;
        if (acceptHeader == null || !acceptHeader.contains("json")) {
            keyType = Key.KeyType.XML;
        }

        Key cacheKey = new Key(
                Key.EntityType.Application,
                appName,
                keyType,
                CurrentRequestVersion.get(),
                EurekaAccept.fromString(eurekaAccept)
        );

        String payLoad = responseCache.get(cacheKey);

        if (payLoad != null) {
            logger.debug("Found: {}", appName);
            return Response.ok(payLoad).build();
        } else {
            logger.debug("Not Found: {}", appName);
            return Response.status(Status.NOT_FOUND).build();
        }
    }

    /**
     * Gets information about a particular instance of an application.
     *
     * @param id
     *            the unique identifier of the instance.
     * @return information about a particular instance.
     */
    @Path("{id}")
    public InstanceResource getInstanceInfo(@PathParam("id") String id) {
        return new InstanceResource(this, id, serverConfig, registry);
    }

    /**
     * Registers information about a particular instance for an
     * {@link com.netflix.discovery.shared.Application}.
     *
     * @param info
     *            {@link InstanceInfo} information of the instance.
     * @param isReplication
     *            a header parameter containing information whether this is
     *            replicated from other nodes.
     */
    @POST
    @Consumes({"application/json", "application/xml"})
    public void addInstance(InstanceInfo info,
                            @HeaderParam(PeerEurekaNode.HEADER_REPLICATION) String isReplication) {
        logger.debug("Registering instance {} (replication={})", info.getId(), isReplication);
        registry.register(info, "true".equals(isReplication));
    }

    /**
     * Returns the application name of a particular application.
     *
     * @return the application name of a particular application.
     */
    String getName() {
        return appName;
    }

}
