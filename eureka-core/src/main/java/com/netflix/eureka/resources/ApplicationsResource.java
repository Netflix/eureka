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

import java.net.URI;
import java.util.Arrays;

import javax.annotation.Nullable;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.eureka.CurrentRequestVersion;
import com.netflix.eureka.EurekaServerConfig;
import com.netflix.eureka.EurekaServerConfigurationManager;
import com.netflix.eureka.InstanceRegistry;
import com.netflix.eureka.PeerAwareInstanceRegistry;
import com.netflix.eureka.PeerAwareInstanceRegistry.Action;
import com.netflix.eureka.Version;
import com.netflix.eureka.cluster.PeerEurekaNode;
import com.netflix.eureka.resources.ResponseCache.Key;
import com.netflix.eureka.resources.ResponseCache.KeyType;
import com.netflix.eureka.util.EurekaMonitors;

/**
 * A <em>jersey</em> resource that handles request related to all
 * {@link Applications}.
 * 
 * @author Karthik Ranganathan, Greg Kim
 * 
 */
@Path("/{version}/apps")
@Produces({ "application/xml", "application/json" })
public class ApplicationsResource {
    private static final String REPLICATION = "true";
    private static final String HEADER_ACCEPT = "Accept";
    private static final String HEADER_ACCEPT_ENCODING = "Accept-Encoding";
    private static final String HEADER_CONTENT_ENCODING = "Content-Encoding";
    private static final String HEADER_GZIP_VALUE = "gzip";
    private static final String HEADER_JSON_VALUE = "json";
    private static final EurekaServerConfig eurekaConfig = EurekaServerConfigurationManager
    .getInstance().getConfiguration();
    
    private static final Logger logger = LoggerFactory.getLogger(ApplicationResource.class);
    
    private final static PeerAwareInstanceRegistry registry = PeerAwareInstanceRegistry
    .getInstance();


    /**
     * Gets information about a particular {@link Application}.
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
        return new ApplicationResource(appId);
    }

    /**
     * Get information about all {@link Applications}.
     * 
     * @param version
     *            the version of the request.
     * @param acceptHeader
     *            the accept header of the request to indicate whether to serve
     *            JSON or XML data.
     * 
     * @param acceptEncoding
     *            the accept header of the request to indicate whether to serve
     *            compressed or uncompressed data.
     * @param uriInfo
     *            the {@link URI} information of the request made.
     * @param regionsStr A comma separated list of remote regions from which the
     *                instances will also be returned. The applications returned
     *                from the remote region can be limited to the applications
     *                returned by {@link EurekaServerConfig#getRemoteRegionAppWhitelist(String)}
     * @return response containing information about all {@link Applications}
     *         from the {@link InstanceRegistry}.
     */
    @GET
    public Response getContainers(@PathParam("version") String version,
            @HeaderParam(HEADER_ACCEPT) String acceptHeader,
            @HeaderParam(HEADER_ACCEPT_ENCODING) String acceptEncoding,
            @Context UriInfo uriInfo, @Nullable @QueryParam("regions") String regionsStr) {

        boolean isRemoteRegionRequested = null != regionsStr && !regionsStr.isEmpty();
        String[] regions = null;
        String normalizedRegionStr = null;
        if (!isRemoteRegionRequested) {
            EurekaMonitors.GET_ALL.increment();
        } else {
            regions = regionsStr.toLowerCase().split(",");
            Arrays.sort(regions); // So, that we don't have different caches for same regions queried in different order.
            normalizedRegionStr = Joiner.on(",").join(regions);
            EurekaMonitors.GET_ALL_WITH_REMOTE_REGIONS.increment();
        }
        // Check if the server allows the access to the registry. The server can
        // restrict access if it is not
        // ready to serve traffic depending on various reasons.
        if (!PeerAwareInstanceRegistry.getInstance().shouldAllowAccess()) {
            return Response.status(Status.FORBIDDEN).build();
        }
        CurrentRequestVersion.set(Version.toEnum(version));
        KeyType keyType = KeyType.JSON;
        if (acceptHeader == null || !acceptHeader.contains(HEADER_JSON_VALUE)) {
            keyType = KeyType.XML;
        }

        Key cacheKey = new Key(Key.EntityType.Application, ResponseCache.ALL_APPS, regions, keyType,
                               CurrentRequestVersion.get());

        if (acceptEncoding != null
            && acceptEncoding.contains(HEADER_GZIP_VALUE)) {
            return Response.ok(ResponseCache.getInstance().getGZIP(cacheKey))
                           .header(HEADER_CONTENT_ENCODING, HEADER_GZIP_VALUE).build();
        } else {
            return Response.ok(ResponseCache.getInstance().get(cacheKey))
                           .build();
        }
    }

    /**
     * Get information about all delta changes in {@link Applications}.
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
     * @param version
     *            the version of the request.
     * @param acceptHeader
     *            the accept header of the request to indicate whether to serve
     *            JSON or XML data.
     * 
     * @param acceptEncoding
     *            the accept header of the request to indicate whether to serve
     *            compressed or uncompressed data.
     * @param uriInfo
     *            the {@link URI} information of the request made.
     * @return response containing the delta information of the
     *         {@link InstanceRegistry}.z
     */
    @Path("delta")
    @GET
    public Response getContainerDifferential(
            @PathParam("version") String version,
            @HeaderParam(HEADER_ACCEPT) String acceptHeader,
            @HeaderParam(HEADER_ACCEPT_ENCODING) String acceptEncoding,
            @Context UriInfo uriInfo, @Nullable @QueryParam("regions") String regionsStr) {
        // If the delta flag is disabled in discovery or if the lease expiration
        // has been disabled, redirect clients to get all instances
        if ((eurekaConfig.shouldDisableDelta())
                ||  (!PeerAwareInstanceRegistry
                                .getInstance().shouldAllowAccess())) {
            return Response.status(Status.FORBIDDEN).build();
        }

        boolean isRemoteRegionRequested = null != regionsStr && !regionsStr.isEmpty();
        String[] regions = null;
        String normalizedRegionStr = null;
        if (!isRemoteRegionRequested) {
            EurekaMonitors.GET_ALL_DELTA.increment();
        } else {
            regions = regionsStr.toLowerCase().split(",");
            Arrays.sort(regions); // So, that we don't have different caches for same regions queried in different order.
            normalizedRegionStr = Joiner.on(",").join(regions);
            EurekaMonitors.GET_ALL_DELTA_WITH_REMOTE_REGIONS.increment();
        }

        CurrentRequestVersion.set(Version.toEnum(version));
        KeyType keyType = KeyType.JSON;
        if (acceptHeader == null || !acceptHeader.contains(HEADER_JSON_VALUE)) {
            keyType = KeyType.XML;
        }
        Key cacheKey = new Key(Key.EntityType.Application, ResponseCache.ALL_APPS_DELTA, regions, keyType,
                               CurrentRequestVersion.get());
        if (acceptEncoding != null
                && acceptEncoding.contains(HEADER_GZIP_VALUE)) {
            return Response.ok(ResponseCache.getInstance().getGZIP(cacheKey))
            .header(HEADER_CONTENT_ENCODING, HEADER_GZIP_VALUE).build();
        } else {
            return Response.ok(ResponseCache.getInstance().get(cacheKey))
            .build();
        }
    }
   
   
    /**
     * Process batched replication events from peer eureka nodes.
     * 
     * <p>
     *  The batched events are delegated to underlying resources to generate a {@link PeerEurekaNode.ReplicationListResponse} 
     *  containing the individual responses to the batched events
     * </p>
     * 
     * @param replicationList
     *            The List of replication events from peer eureka nodes
      * @return A batched response containing the information about the responses of individual events
     */
    @Path("batch")
    @POST
    public Response batchReplication(
            PeerEurekaNode.ReplicationList replicationList) {
        Response response = null;
        try {

            PeerEurekaNode.ReplicationListResponse batchResponse = new PeerEurekaNode.ReplicationListResponse();
            for (PeerEurekaNode.ReplicationInstance instanceInfo : replicationList
                    .getList()) {
                ApplicationResource applicationResource = new ApplicationResource(
                        instanceInfo.getAppName());
                InstanceResource resource = new InstanceResource(
                        applicationResource, instanceInfo.getId());
                String lastDirtyTimestamp = (instanceInfo
                        .getLastDirtyTimestamp() == null ? null : instanceInfo
                                .getLastDirtyTimestamp().toString());
                String overriddenStatus = (instanceInfo.getOverriddenStatus() == null ? null
                        : instanceInfo.getOverriddenStatus());
                String instanceStatus = (instanceInfo.getStatus() == null ? null
                        : instanceInfo.getStatus());
                PeerEurekaNode.ReplicationInstanceResponse.Builder singleResponseBuilder = new PeerEurekaNode.ReplicationInstanceResponse.Builder();
                if (replicationList.getAction() == Action.Heartbeat) {
                    response = resource.renewLease(REPLICATION, overriddenStatus,
                            instanceStatus, lastDirtyTimestamp);

                    singleResponseBuilder.setStatusCode(response.getStatus());
                    if (response.getStatus() == Response.Status.OK
                            .getStatusCode() && response.getEntity() != null) {
                        singleResponseBuilder
                        .setResponseEntity((InstanceInfo) response
                                .getEntity());
                    }
                } else if (replicationList.getAction() == Action.Register) {
                    applicationResource.addInstance(
                            instanceInfo.getInstanceInfo(), REPLICATION);

                    singleResponseBuilder = new PeerEurekaNode.ReplicationInstanceResponse.Builder()
                    .setStatusCode(Status.OK.getStatusCode());
                } else if (replicationList.getAction() == Action.StatusUpdate) {
                    response = resource.statusUpdate(instanceInfo.getStatus(),
                            REPLICATION, instanceInfo.getLastDirtyTimestamp()
                            .toString());

                    singleResponseBuilder = new PeerEurekaNode.ReplicationInstanceResponse.Builder()
                    .setStatusCode(response.getStatus());
                } else if (replicationList.getAction() == Action.Cancel) {
                    response = resource.cancelLease(REPLICATION);

                    singleResponseBuilder = new PeerEurekaNode.ReplicationInstanceResponse.Builder()
                    .setStatusCode(response.getStatus());
                }

                batchResponse.addResponse(singleResponseBuilder.build());
            }
            return Response.ok(batchResponse).build();
        } catch (Throwable e) {
            logger.error("Cannot execute batch Request", e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();

        }

    }
}
