/*
 * AppResource.java
 *
 * $Header: //depot/commonlibraries/platform/cloud/src/com/netflix/appinfo/InstanceInfo.java#2 $
 * $DateTime: 2009/01/29 16:37:50 $
 *
 * Copyright (c) 2009 Netflix, Inc.  All rights reserved.
 */
package com.netflix.discovery.resources;

import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import com.netflix.config.DynamicBooleanProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.discovery.CurrentRequestVersion;
import com.netflix.discovery.ReplicaAwareInstanceRegistry;
import com.netflix.discovery.Version;
import com.netflix.discovery.resources.ResponseCache.Key;
import com.netflix.discovery.resources.ResponseCache.KeyType;
import com.netflix.discovery.util.DSCounter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Root resource
 *
 * @author gkim
 */

@Path("/{version}/apps")
@Produces({"application/xml","application/json"})
public class ApplicationsResource {
    //TODO: add a filter to set version instead
    private static final String HEADER_ACCEPT           =   "Accept";
    private static final String HEADER_ACCEPT_ENCODING  =   "Accept-Encoding";
    private static final String HEADER_CONTENT_ENCODING =   "Content-Encoding";
    private static final String HEADER_GZIP_VALUE       =   "gzip";
    private static final String HEADER_JSON_VALUE       =   "json";
    private static final DynamicBooleanProperty DISABLE_DELTA_PROPERTY = DynamicPropertyFactory.getInstance().getBooleanProperty("netflix.discovery.disableDeltaInServer", false);
    private static final Logger logger = LoggerFactory.getLogger(ApplicationResource.class); 
    
    @Path("{appId}")
    public ApplicationResource getApplicationResource(
            @PathParam("version") String version,
            @PathParam("appId") String appId) {
        CurrentRequestVersion.set(Version.toEnum(version));
        return new ApplicationResource(appId);
    }

    @GET
    public Response getContainers(
            @PathParam("version") String version,
            @HeaderParam(HEADER_ACCEPT) String acceptHeader,
            @HeaderParam(HEADER_ACCEPT_ENCODING) String acceptEncoding,
            @Context UriInfo uriInfo) {
        
        DSCounter.GET_ALL.increment();
        if  (!ReplicaAwareInstanceRegistry.getInstance().shouldAllowAccess()) {
           return Response.status(Status.FORBIDDEN).build();
        }
        CurrentRequestVersion.set(Version.toEnum(version));
        KeyType keyType = KeyType.JSON;
        if(acceptHeader == null || !acceptHeader.contains(HEADER_JSON_VALUE)){
            keyType = KeyType.XML;
        }
        Key cacheKey = new Key(ResponseCache.ALL_APPS, keyType, CurrentRequestVersion.get());
        if(acceptEncoding != null && acceptEncoding.contains(HEADER_GZIP_VALUE)){
            return Response.ok(ResponseCache.getInstance().getGZIP(cacheKey)).
                header(HEADER_CONTENT_ENCODING, HEADER_GZIP_VALUE).build();
        }else {
            return Response.ok(ResponseCache.getInstance().get(cacheKey)).build();
        }
    }
    
    @Path("delta")
    @GET
    public Response getContainerDifferential(
            @PathParam("version") String version,
            @HeaderParam(HEADER_ACCEPT) String acceptHeader,
            @HeaderParam(HEADER_ACCEPT_ENCODING) String acceptEncoding,
            @Context UriInfo uriInfo) {
        // If the delta flag is disabled in discovery or if the lease expiration has been disabled, redirect clients to get all instances
        if ((DISABLE_DELTA_PROPERTY.get())
                || (!ReplicaAwareInstanceRegistry.getInstance()
                        .isLeaseExpirationEnabled() || (!ReplicaAwareInstanceRegistry
                        .getInstance().shouldAllowAccess()))) {
            return Response.status(Status.FORBIDDEN).build();
        }
        DSCounter.GET_ALL_DELTA.increment();
        CurrentRequestVersion.set(Version.toEnum(version));
        KeyType keyType = KeyType.JSON;
        if(acceptHeader == null || !acceptHeader.contains(HEADER_JSON_VALUE)){
            keyType = KeyType.XML;
        }
        Key cacheKey = new Key(ResponseCache.ALL_APPS_DELTA, keyType, CurrentRequestVersion.get());
        if(acceptEncoding != null && acceptEncoding.contains(HEADER_GZIP_VALUE)){
            return Response.ok(ResponseCache.getInstance().getGZIP(cacheKey)).
                header(HEADER_CONTENT_ENCODING, HEADER_GZIP_VALUE).build();
        }else {
            return Response.ok(ResponseCache.getInstance().get(cacheKey)).build();
        }
    }
}

