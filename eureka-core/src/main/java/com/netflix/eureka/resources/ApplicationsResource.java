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

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;
import java.util.Arrays;

import com.netflix.appinfo.EurekaAccept;
import com.netflix.eureka.EurekaServerContext;
import com.netflix.eureka.EurekaServerContextHolder;
import com.netflix.eureka.registry.AbstractInstanceRegistry;
import com.netflix.eureka.EurekaServerConfig;
import com.netflix.eureka.registry.PeerAwareInstanceRegistry;
import com.netflix.eureka.Version;
import com.netflix.eureka.registry.ResponseCache;
import com.netflix.eureka.registry.Key.KeyType;
import com.netflix.eureka.registry.ResponseCacheImpl;
import com.netflix.eureka.registry.Key;
import com.netflix.eureka.util.EurekaMonitors;

/**
 * A <em>jersey</em> resource that handles request related to all
 * {@link com.netflix.discovery.shared.Applications}.
 *
 * @author Karthik Ranganathan, Greg Kim
 *
 */
@Path("/{version}/apps")
@Produces({"application/xml", "application/json"})
public class ApplicationsResource {
    private static final String HEADER_ACCEPT = "Accept";
    private static final String HEADER_ACCEPT_ENCODING = "Accept-Encoding";
    private static final String HEADER_CONTENT_ENCODING = "Content-Encoding";
    private static final String HEADER_CONTENT_TYPE = "Content-Type";
    private static final String HEADER_GZIP_VALUE = "gzip";
    private static final String HEADER_JSON_VALUE = "json";

    private final EurekaServerConfig serverConfig;
    private final PeerAwareInstanceRegistry registry;
    private final ResponseCache responseCache;

    @Inject
    ApplicationsResource(EurekaServerContext eurekaServer) {
        this.serverConfig = eurekaServer.getServerConfig();
        this.registry = eurekaServer.getRegistry();
        this.responseCache = registry.getResponseCache();
    }

    public ApplicationsResource() {
        this(EurekaServerContextHolder.getInstance().getServerContext());
    }

    /**
     * Gets information about a particular {@link com.netflix.discovery.shared.Application}.
     *
     * @param version
     *            the version of the request.
     * @param appId
     *            the unique application identifier (which is the name) of the
     *            application.
     * @return information about a particular application.
     */
    @Path("{appId}")
    public ApplicationResource getApplicationResource(
            @PathParam("version") String version,
            @PathParam("appId") String appId) {
        CurrentRequestVersion.set(Version.toEnum(version));
        try {
            return new ApplicationResource(appId, serverConfig, registry);
        } finally {
            CurrentRequestVersion.remove();
        }
    }

    /**
     * Get information about all {@link com.netflix.discovery.shared.Applications}.
     *
     * @param version the version of the request.
     * @param acceptHeader the accept header to indicate whether to serve JSON or XML data.
     * @param acceptEncoding the accept header to indicate whether to serve compressed or uncompressed data.
     * @param eurekaAccept an eureka accept extension, see {@link com.netflix.appinfo.EurekaAccept}
     * @param uriInfo the {@link java.net.URI} information of the request made.
     * @param regionsStr A comma separated list of remote regions from which the instances will also be returned.
     *                   The applications returned from the remote region can be limited to the applications
     *                   returned by {@link EurekaServerConfig#getRemoteRegionAppWhitelist(String)}
     *
     * @return a response containing information about all {@link com.netflix.discovery.shared.Applications}
     *         from the {@link AbstractInstanceRegistry}.
     */
    @GET
    public Response getContainers(@PathParam("version") String version,
                                  @HeaderParam(HEADER_ACCEPT) String acceptHeader,
                                  @HeaderParam(HEADER_ACCEPT_ENCODING) String acceptEncoding,
                                  @HeaderParam(EurekaAccept.HTTP_X_EUREKA_ACCEPT) String eurekaAccept,
                                  @Context UriInfo uriInfo,
                                  @Nullable @QueryParam("regions") String regionsStr) {

        boolean isRemoteRegionRequested = null != regionsStr && !regionsStr.isEmpty();
        String[] regions = null;
        if (!isRemoteRegionRequested) {
            EurekaMonitors.GET_ALL.increment();
        } else {
            regions = regionsStr.toLowerCase().split(",");
            Arrays.sort(regions); // So we don't have different caches for same regions queried in different order.
            EurekaMonitors.GET_ALL_WITH_REMOTE_REGIONS.increment();
        }

        // Check if the server allows the access to the registry. The server can
        // restrict access if it is not
        // ready to serve traffic depending on various reasons.
        if (!registry.shouldAllowAccess(isRemoteRegionRequested)) {
            return Response.status(Status.FORBIDDEN).build();
        }
        CurrentRequestVersion.set(Version.toEnum(version));
        KeyType keyType = Key.KeyType.JSON;
        String returnMediaType = MediaType.APPLICATION_JSON;
        if (acceptHeader == null || !acceptHeader.contains(HEADER_JSON_VALUE)) {
            keyType = Key.KeyType.XML;
            returnMediaType = MediaType.APPLICATION_XML;
        }

        Key cacheKey = new Key(Key.EntityType.Application,
                ResponseCacheImpl.ALL_APPS,
                keyType, CurrentRequestVersion.get(), EurekaAccept.fromString(eurekaAccept), regions
        );

        Response response;
        if (acceptEncoding != null && acceptEncoding.contains(HEADER_GZIP_VALUE)) {
            response = Response.ok(responseCache.getGZIP(cacheKey))
                    .header(HEADER_CONTENT_ENCODING, HEADER_GZIP_VALUE)
                    .header(HEADER_CONTENT_TYPE, returnMediaType)
                    .build();
        } else {
            response = Response.ok(responseCache.get(cacheKey))
                    .build();
        }
        CurrentRequestVersion.remove();
        return response;
    }

    /**
     * Get information about all delta changes in {@link com.netflix.discovery.shared.Applications}.
     *
     * <p>
     * The delta changes represent the registry information change for a period
     * as configured by
     * {@link EurekaServerConfig#getRetentionTimeInMSInDeltaQueue()}. The
     * changes that can happen in a registry include
     * <em>Registrations,Cancels,Status Changes and Expirations</em>. Normally
     * the changes to the registry are infrequent and hence getting just the
     * delta will be much more efficient than getting the complete registry.
     * </p>
     *
     * <p>
     * Since the delta information is cached over a period of time, the requests
     * may return the same data multiple times within the window configured by
     * {@link EurekaServerConfig#getRetentionTimeInMSInDeltaQueue()}.The clients
     * are expected to handle this duplicate information.
     * <p>
     *
     * @param version the version of the request.
     * @param acceptHeader the accept header to indicate whether to serve  JSON or XML data.
     * @param acceptEncoding the accept header to indicate whether to serve compressed or uncompressed data.
     * @param eurekaAccept an eureka accept extension, see {@link com.netflix.appinfo.EurekaAccept}
     * @param uriInfo  the {@link java.net.URI} information of the request made.
     * @return response containing the delta information of the
     *         {@link AbstractInstanceRegistry}.
     */
    @Path("delta")
    @GET
    public Response getContainerDifferential(
            @PathParam("version") String version,
            @HeaderParam(HEADER_ACCEPT) String acceptHeader,
            @HeaderParam(HEADER_ACCEPT_ENCODING) String acceptEncoding,
            @HeaderParam(EurekaAccept.HTTP_X_EUREKA_ACCEPT) String eurekaAccept,
            @Context UriInfo uriInfo, @Nullable @QueryParam("regions") String regionsStr) {

        boolean isRemoteRegionRequested = null != regionsStr && !regionsStr.isEmpty();

        // If the delta flag is disabled in discovery or if the lease expiration
        // has been disabled, redirect clients to get all instances
        if ((serverConfig.shouldDisableDelta()) || (!registry.shouldAllowAccess(isRemoteRegionRequested))) {
            return Response.status(Status.FORBIDDEN).build();
        }

        String[] regions = null;
        if (!isRemoteRegionRequested) {
            EurekaMonitors.GET_ALL_DELTA.increment();
        } else {
            regions = regionsStr.toLowerCase().split(",");
            Arrays.sort(regions); // So we don't have different caches for same regions queried in different order.
            EurekaMonitors.GET_ALL_DELTA_WITH_REMOTE_REGIONS.increment();
        }

        CurrentRequestVersion.set(Version.toEnum(version));
        KeyType keyType = Key.KeyType.JSON;
        String returnMediaType = MediaType.APPLICATION_JSON;
        if (acceptHeader == null || !acceptHeader.contains(HEADER_JSON_VALUE)) {
            keyType = Key.KeyType.XML;
            returnMediaType = MediaType.APPLICATION_XML;
        }

        Key cacheKey = new Key(Key.EntityType.Application,
                ResponseCacheImpl.ALL_APPS_DELTA,
                keyType, CurrentRequestVersion.get(), EurekaAccept.fromString(eurekaAccept), regions
        );

        final Response response;

        if (acceptEncoding != null && acceptEncoding.contains(HEADER_GZIP_VALUE)) {
             response = Response.ok(responseCache.getGZIP(cacheKey))
                    .header(HEADER_CONTENT_ENCODING, HEADER_GZIP_VALUE)
                    .header(HEADER_CONTENT_TYPE, returnMediaType)
                    .build();
        } else {
            response = Response.ok(responseCache.get(cacheKey)).build();
        }

        CurrentRequestVersion.remove();
        return response;
    }
}
